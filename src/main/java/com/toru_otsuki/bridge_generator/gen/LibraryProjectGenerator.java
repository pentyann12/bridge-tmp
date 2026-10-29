package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.model.IdlEnum;
import com.toru_otsuki.bridge_generator.model.Service;
import com.toru_otsuki.bridge_generator.model.Struct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// CORBAサービスを型付きのJavaメソッドで呼ぶための共有ライブラリ（Gradleのjava-libraryプロジェクト）を生成する
///
/// RESTブリッジ（[SpringBootProjectGenerator]）とは独立したプロジェクトとして生成する。構造体のDTO/Mapperと型の変換規則
/// （[TypeMapping]）は共通だが、HTTPの部分の代わりに、サービスごとの型付きクライアントと例外クラスを生成する。
public final class LibraryProjectGenerator {
  private LibraryProjectGenerator() {}

  /// 全サービスのクライアントを1つのライブラリにまとめて生成する。操作のないサービスのクライアントは作らない
  ///
  /// @param services サービスのモデル一覧
  /// @param outputDir 生成するプロジェクトのディレクトリ
  /// @param projectName プロジェクト名（jarの名前になる）
  /// @param settings コード生成全体の設定
  /// @throws IOException ファイルの書き込みに失敗した場合
  public static void generate(
      List<Service> services, Path outputDir, String projectName, GenSettings settings)
      throws IOException {
    GenContext ctx = new GenContext(settings, outputDir);
    for (Path dir :
        List.of(
            ctx.dtoDir(),
            ctx.mapperDir(),
            ctx.clientDir(),
            ctx.exceptionDir(),
            ctx.springDir(),
            ctx.resourcesDir())) {
      Files.createDirectories(dir);
    }

    // IDLによらない部分と、構造体・enumのDTO/Mapper（RESTブリッジと共通の生成器）
    LibraryScaffolder.writeBuildFiles(outputDir, projectName);
    StubFiles.copyIdlStubSources(settings.stubRoot(), ctx.javaDir());
    AnyTypeWriter.writeAnyValueDto(ctx, Target.LIBRARY);
    AnyTypeWriter.writeAnyMapper(ctx, settings.structs(), settings.enums(), Target.LIBRARY);
    StringMapperWriter.writeForLibrary(ctx);
    LibraryRuntimeWriter.writeRuntime(ctx);
    for (Struct struct : settings.structs()) {
      DtoGenerator.generate(struct, ctx);
      MapperGenerator.generate(struct, ctx);
      if (struct.exception()) LibraryRuntimeWriter.writeUserException(struct, ctx);
    }
    for (IdlEnum idlEnum : settings.enums()) {
      EnumGenerator.generate(idlEnum, ctx);
    }

    // サービスごとのクライアントと、それをBeanにするSpring Bootの自動設定
    List<Service> withOperations =
        services.stream().filter(s -> !s.allOperations().isEmpty()).toList();
    for (Service service : withOperations) {
      ClientWriter.write(service, ctx);
    }
    LibraryScaffolder.writeSpringAutoConfiguration(withOperations, ctx);
    LibraryScaffolder.writeReadme(withOperations, projectName, ctx);
  }
}
