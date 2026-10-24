package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.stub.StubFiles;
import com.toru_otsuki.bridge_generator.stub.StubModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// CORBAサービスをRESTエンドポイントとして公開するSpring Bootプロジェクト（Gradle）を生成する
///
/// プロジェクトごとに共通部分（ビルド設定・スタブ・構造体のDTO/Mapperなど）を出力し、サービスごとにDTO・CorbaClient・Controllerを出力し、最後にIOR設定用のapplication.ymlを出力する。
public final class SpringBootProjectGenerator {
  private SpringBootProjectGenerator() {}

  /// 1つのサービスから1つのSpring Bootプロジェクトを生成する。クライアントクラス名は`CorbaClient`になる。
  ///
  /// @param service サービスのモデル
  /// @param outputDir 生成するSpring Bootプロジェクトの出力ディレクトリ
  /// @param settings コード生成全体の設定
  /// @throws IOException ファイルの書き込みに失敗した場合
  public static void generate(StubModel.Service service, Path outputDir, GenSettings settings)
      throws IOException {
    GenContext ctx = setupProject(outputDir, settings);
    generateService(service, ctx, "CorbaClient");
    ServiceDtoWriter.writeApplicationProperties(ctx, List.of(service.name()));
  }

  /// 複数のサービスを単一のSpring Bootプロジェクトにまとめて生成する。サービスごとに専用のCorbaClient（例:
  /// `ItemServiceCorbaClient`）とControllerを生成する。
  ///
  /// @param services サービスのモデル一覧
  /// @param outputDir 生成するSpring Bootプロジェクトの出力ディレクトリ
  /// @param settings コード生成全体の設定
  /// @throws IOException ファイルの書き込みに失敗した場合
  public static void generateAll(
      List<StubModel.Service> services, Path outputDir, GenSettings settings) throws IOException {
    GenContext ctx = setupProject(outputDir, settings);
    for (StubModel.Service service : services) {
      generateService(service, ctx, service.name() + "CorbaClient");
    }
    ServiceDtoWriter.writeApplicationProperties(
        ctx, services.stream().map(StubModel.Service::name).toList());
  }

  /// プロジェクトの共通部分（ディレクトリ・ビルドファイル・共有クラス・スタブ・構造体のDTO/Mapper）を生成する
  ///
  /// @param outputDir 生成するSpring Bootプロジェクトの出力ディレクトリ
  /// @param settings コード生成全体の設定
  /// @return このプロジェクト用のコード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  private static GenContext setupProject(Path outputDir, GenSettings settings) throws IOException {
    GenContext ctx = new GenContext(settings, outputDir);
    for (Path dir :
        List.of(
            ctx.controllerDir(),
            ctx.dtoDir(),
            ctx.mapperDir(),
            ctx.corbaDir(),
            ctx.resourcesDir())) {
      Files.createDirectories(dir);
    }

    ProjectScaffolder.writeBuildGradle(outputDir);
    ProjectScaffolder.writeApplicationClass(ctx.basePackage(), ctx.packageDir());
    ProjectScaffolder.writeServletClass(ctx.basePackage(), ctx.packageDir());
    StubFiles.copyIdlStubSources(settings.stubRoot(), ctx.javaDir());
    AnyTypeWriter.writeAnyValueDto(ctx);
    AnyTypeWriter.writeAnyMapper(ctx, settings.structs());
    StringMapperWriter.write(ctx);
    for (StubModel.Struct struct : settings.structs()) {
      DtoGenerator.generate(struct, ctx);
      MapperGenerator.generate(struct, ctx);
    }
    return ctx;
  }

  /// サービス固有のファイル（操作ごとのDTO・CorbaClient・Controller）を生成する。操作のないサービスは何も生成しない。
  ///
  /// @param service サービスのモデル
  /// @param ctx コード生成コンテキスト
  /// @param clientClassName 生成するCorbaClientクラスの名前
  /// @throws IOException ファイルの書き込みに失敗した場合
  private static void generateService(
      StubModel.Service service, GenContext ctx, String clientClassName) throws IOException {
    if (service.operations().isEmpty()) return;
    for (StubModel.Operation op : service.operations()) {
      ServiceDtoWriter.generateRequestDto(op, ctx);
      ServiceDtoWriter.generateRpcResponse(op, ctx);
    }
    CorbaClientWriter.write(service, clientClassName, ctx);
    ControllerWriter.write(service, clientClassName, ctx);
  }
}
