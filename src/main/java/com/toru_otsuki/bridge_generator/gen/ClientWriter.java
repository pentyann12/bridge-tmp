package com.toru_otsuki.bridge_generator.gen;

import com.palantir.javapoet.CodeBlock;
import com.toru_otsuki.bridge_generator.model.CorbaType;
import com.toru_otsuki.bridge_generator.model.Operation;
import com.toru_otsuki.bridge_generator.model.Param;
import com.toru_otsuki.bridge_generator.model.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/// 共有ライブラリの、サービスごとの型付きクライアント（`XxxClient`）を生成する
///
/// 利用者にはDTOとJavaの型だけを見せ、CORBAの事情は次のように隠す。
///
/// - in/inout引数はメソッドの引数で、戻り値とout/inout引数は1つの戻り値で受け渡す。out/inout引数がある操作は、
///   それらをまとめた結果のrecord（`XxxClient.FindItemsResult`など）を返す
/// - DTOとCORBAの型の変換、文字コードの読み替えは[TypeMapping]の規則で行う
/// - ユーザー例外はライブラリの例外クラスに、システム例外は`CorbaCallException`に包み直す
/// - サービスのオブジェクト参照は最初の呼び出しで解決する。CORBAサーバーが止まっていても、利用側のアプリは起動できる
final class ClientWriter {
  private ClientWriter() {}

  /// クライアントクラスの名前を返す
  ///
  /// @param service サービスのモデル
  /// @return クライアントクラスの単純名
  static String className(Service service) {
    return service.name() + "Client";
  }

  /// サービス1つ分のクライアントクラスを出力する
  ///
  /// @param service サービスのモデル
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void write(Service service, GenContext ctx) throws IOException {
    String stub = qualified(service.pkg(), service.name());
    String methods =
        service.operations().stream()
            .map(op -> method(service, op, ctx))
            .collect(Collectors.joining("\n"));
    String records =
        service.operations().stream()
            .filter(Operation::hasOutParams)
            .map(op -> resultRecord(op, ctx))
            .collect(Collectors.joining("\n"));

    Files.writeString(
        ctx.clientDir().resolve(className(service) + ".java"),
        """
        package %1$s.client;

        /**
         * CORBAサービス %2$s を呼ぶクライアント。スレッドセーフ。
         *
         * <p>失敗時は、IDLのraisesで宣言された例外ならその例外クラスを、通信の失敗などなら CorbaCallException を投げる。
         */
        public final class %3$s {
          private final CorbaConnection connection;
          private final String ior;
          private volatile %4$s service;

          public %3$s(CorbaConnection connection, String ior) {
            this.connection = java.util.Objects.requireNonNull(connection, "connection");
            this.ior = java.util.Objects.requireNonNull(ior, "ior");
          }

          private %4$s stub() {
            %4$s s = service;
            if (s == null) {
              s = connection.resolve(ior, %4$sHelper::narrow);
              service = s;
            }
            return s;
          }

        %5$s%6$s}
        """
            .formatted(
                ctx.basePackage(),
                service.name(),
                className(service),
                stub,
                methods,
                records.isEmpty() ? "" : "\n" + records));
  }

  /// 操作1つ分のメソッドを返す
  private static String method(Service service, Operation op, GenContext ctx) {
    TypeMapping types = ctx.types();

    // in/inout引数をメソッドの引数にする
    String params =
        op.params().stream()
            .filter(Param::isInput)
            .map(p -> types.dtoType(p.type()) + " " + p.name())
            .collect(Collectors.joining(", "));
    String returnType =
        op.hasOutParams()
            ? resultRecordName(op)
            : op.returnType().map(t -> types.dtoType(t).toString()).orElse("void");

    // 呼び出しの準備。省略できないinout引数の確認と、out/inout引数のHolderの用意
    StringBuilder prepare = new StringBuilder();
    List<String> args = new ArrayList<>();
    for (Param p : op.params()) {
      String fromDto = types.fromDto(p.type(), CodeBlock.of("$L", p.name())).toString();
      switch (p.direction()) {
        case IN -> args.add(fromDto);
        case OUT -> {
          prepare.append("      %1$s __%2$s = new %1$s();\n".formatted(p.holderType(), p.name()));
          args.add("__" + p.name());
        }
        case INOUT -> {
          if (TypeMapping.needsValue(p.type())) {
            prepare.append(
                "      java.util.Objects.requireNonNull(%1$s, \"%1$s\");\n".formatted(p.name()));
          }
          prepare.append(
              "      %1$s __%2$s = new %1$s(%3$s);\n".formatted(p.holderType(), p.name(), fromDto));
          args.add("__" + p.name());
        }
      }
    }

    // 呼び出しと、戻り値・out/inout引数のDTOへの変換
    String call = "stub().%s(%s)".formatted(op.name(), String.join(", ", args));
    String invoke;
    if (op.hasOutParams()) {
      List<String> components = new ArrayList<>();
      op.returnType().ifPresent(t -> components.add(toDto(t, "__ret", types)));
      for (Param p : op.params()) {
        if (p.isOut()) components.add(toDto(p.type(), "__" + p.name() + ".value", types));
      }
      invoke =
          op.returnType()
                  .map(t -> "      %s __ret = %s;\n".formatted(types.corbaType(t), call))
                  .orElse("      %s;\n".formatted(call))
              + "      return new %s(%s);\n"
                  .formatted(resultRecordName(op), String.join(", ", components));
    } else if (op.returnType().isPresent()) {
      CorbaType t = op.returnType().get();
      invoke =
          "      %s __ret = %s;\n      return %s;\n"
              .formatted(types.corbaType(t), call, toDto(t, "__ret", types));
    } else {
      invoke = "      %s;\n".formatted(call);
    }

    // ユーザー例外は宣言順に、最後にシステム例外を包み直す
    String catches =
        op.raises().stream()
                .map(
                    e ->
                        """
                            } catch (%s __e) {
                              throw new %s.exception.%s(%s, __e);
                        """
                            .formatted(
                                types.corbaType(e),
                                ctx.basePackage(),
                                LibraryRuntimeWriter.exceptionClassName(e.simpleName()),
                                types.toDto(e, CodeBlock.of("__e"), true)))
                .collect(Collectors.joining())
            + """
                } catch (org.omg.CORBA.SystemException __e) {
                  throw new %s.exception.CorbaCallException("%s.%s", __e);
                }
            """
                .formatted(ctx.basePackage(), service.name(), op.name());

    return """
          public %s %s(%s) {
            try {
        %s%s%s  }
        """
        .formatted(returnType, op.name(), params, prepare, invoke, catches);
  }

  /// out/inout引数がある操作の結果をまとめるrecordを返す。戻り値は`returnValue`、out/inout引数は引数名の要素にする
  private static String resultRecord(Operation op, GenContext ctx) {
    TypeMapping types = ctx.types();
    List<String> components = new ArrayList<>();
    op.returnType().ifPresent(t -> components.add(types.dtoType(t) + " returnValue"));
    for (Param p : op.params()) {
      if (p.isOut()) components.add(types.dtoType(p.type()) + " " + p.name());
    }
    return """
          /** %s の結果（戻り値とout/inout引数）。 */
          public record %s(%s) {}
        """
        .formatted(op.name(), resultRecordName(op), String.join(", ", components));
  }

  private static String resultRecordName(Operation op) {
    return op.capitalizedName() + "Result";
  }

  private static String toDto(CorbaType type, String expr, TypeMapping types) {
    return types.toDto(type, CodeBlock.of("$L", expr), true).toString();
  }

  private static String qualified(String pkg, String name) {
    return pkg.isEmpty() ? name : pkg + "." + name;
  }
}
