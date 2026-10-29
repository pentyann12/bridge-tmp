package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.model.CorbaType;
import com.toru_otsuki.bridge_generator.model.IdlEnum;
import com.toru_otsuki.bridge_generator.model.Operation;
import com.toru_otsuki.bridge_generator.model.Service;
import com.toru_otsuki.bridge_generator.model.Struct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/// 生成するクラスの名前やパッケージが、ほかのクラスと重ならないかを生成の前に調べる
///
/// [Naming]でプレフィックスやサフィックスを変えたり空にしたりすると、IDLの型名や操作名しだいで名前が重なることがある。
/// 重なったまま生成すると、ファイルが上書きされたり、生成コードが別のクラスを指してコンパイルできなくなったりする。
public final class NameConflicts {
  /// basePackageの下に生成するパッケージ（空はbasePackageそのもの）
  private static final List<String> GENERATED_SUBPACKAGES =
      List.of("", "dto", "mapper", "controller", "corba", "client", "exception", "spring");

  /// RESTブリッジが操作ごとにdtoパッケージに置くクラスの、操作名の後ろに付く部分
  private static final List<String> REST_OPERATION_SUFFIXES =
      List.of("RequestDto", "RpcRequest", "RpcResult", "RpcResponse");

  private NameConflicts() {}

  /// 重なりを調べる
  ///
  /// @param services サービスのモデル一覧
  /// @param settings コード生成全体の設定
  /// @param target 生成するプロジェクトの種類
  /// @return 重なりの説明（利用者に表示する）。無ければ空
  public static List<String> find(List<Service> services, GenSettings settings, Target target) {
    List<String> problems = new ArrayList<>();
    checkPackages(services, settings, problems);
    checkDtoNames(services, settings, target, problems);
    if (target == Target.LIBRARY) checkResultNames(services, settings, problems);
    return problems;
  }

  /// idljのクラスが、生成するクラスと同じパッケージに入らないこと。同じ名前ならファイルが上書きされる
  private static void checkPackages(
      List<Service> services, GenSettings settings, List<String> problems) {
    Set<String> generated = new LinkedHashSet<>();
    for (String sub : GENERATED_SUBPACKAGES) {
      generated.add(sub.isEmpty() ? settings.basePackage() : settings.basePackage() + "." + sub);
    }
    Stream.of(
            services.stream().map(Service::pkg),
            settings.structs().stream().map(Struct::pkg),
            settings.enums().stream().map(IdlEnum::pkg))
        .flatMap(s -> s)
        .filter(generated::contains)
        .distinct()
        .forEach(
            pkg ->
                problems.add(
                    "IDLから生成したクラスのパッケージ %s が、生成するプロジェクトのパッケージと同じです。--idl-package-prefix か --base-package を変えてください"
                        .formatted(pkg)));
  }

  /// dtoパッケージの中で、構造体・enumのDTOがほかのクラスと同じ名前にならないこと
  private static void checkDtoNames(
      List<Service> services, GenSettings settings, Target target, List<String> problems) {
    Map<String, String> owners = new LinkedHashMap<>();
    owners.put("AnyValue", "anyのDTO");
    if (target == Target.REST) {
      for (Service service : services) {
        for (Operation op : service.operations()) {
          for (String suffix : REST_OPERATION_SUFFIXES) {
            owners.putIfAbsent(
                op.capitalizedName() + suffix, "操作 %s.%s".formatted(service.name(), op.name()));
          }
        }
      }
    }
    Stream<CorbaType.Named> types =
        Stream.concat(
            settings.structs().stream().map(Struct::type),
            settings.enums().stream().map(IdlEnum::type));
    for (CorbaType.Named type : (Iterable<CorbaType.Named>) types::iterator) {
      String name = settings.naming().dtoName(type.simpleName());
      String owner = "型 " + qualified(type);
      String other = owners.putIfAbsent(name, owner);
      if (other != null) {
        problems.add(
            "%sのDTOの名前 %s が、%sのクラスと重なります。--dto-prefix か --dto-suffix を変えてください"
                .formatted(owner, name, other));
      } else if (isJavaLang(name)) {
        problems.add(
            "%sのDTOの名前 %s が、java.langのクラスと重なります。--dto-prefix か --dto-suffix を変えてください"
                .formatted(owner, name));
      }
    }
  }

  /// クライアントクラスの中で、結果のrecordが、クライアントが名前で参照するクラスと同じ名前にならないこと
  private static void checkResultNames(
      List<Service> services, GenSettings settings, List<String> problems) {
    for (Service service : services) {
      String client = ClientWriter.className(service);
      for (Operation op : service.allOperations()) {
        if (!op.hasOutParams()) continue;
        String name = settings.naming().resultName(op);
        if (name.equals(client) || name.equals("CorbaConnection") || isJavaLang(name)) {
          problems.add(
              "操作 %s.%s の結果のrecordの名前 %s が、%s の中で使うクラスと重なります。--result-prefix か --result-suffix を変えてください"
                  .formatted(service.name(), op.name(), name, client));
        }
      }
    }
  }

  /// 生成コードは`String`などを単純名で書くので、同じ名前のクラスがあると、そちらを指してしまう
  private static boolean isJavaLang(String simpleName) {
    try {
      Class.forName("java.lang." + simpleName, false, null);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static String qualified(CorbaType.Named type) {
    return type.pkg().isEmpty() ? type.simpleName() : type.pkg() + "." + type.simpleName();
  }
}
