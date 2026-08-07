package com.toru_otsuki.bridge_generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CORBAサービスをRESTエンドポイントとして公開するSpring Bootプロジェクト（Gradle）を生成するユーティリティ
 *
 * <p>主な生成フロー:
 *
 * <p>1. `#setupProject` でディレクトリ・共通ファイル・スタブコピー・構造体DTO/Mapperを出力 2. `#generateService`
 * でサービスごとのRequestDto/ResponseDto・CorbaClient・Controllerを出力 3.
 * `ServiceDtoWriter#writeApplicationProperties` でIOR設定ファイルを出力
 */
public class SpringBootProjectGenerator {

  /**
   * 単一のCORBAサービスインターフェースからSpring Bootプロジェクトを生成
   *
   * @param serviceIface CORBAサービスインターフェースのクラス宣言
   * @param stubSourceRoot idljによって生成されたスタブソースのルートディレクトリ
   * @param outputDir 生成するSpring Bootプロジェクトの出力ディレクトリ
   * @param basePkgName 生成プロジェクトのベースパッケージ名
   * @param returnAsDto 複雑な戻り値型をDTOに変換するかどうか
   * @param idlPackagePrefix `idlj -pkgPrefix`に渡したパッケージプレフィックス
   * @param repoIdPrefix IDLの`#pragma prefix`で設定されたリポジトリIDプレフィックス
   */
  public static void generate(
      ClassOrInterfaceDeclaration serviceIface,
      Path stubSourceRoot,
      Path outputDir,
      String basePkgName,
      boolean returnAsDto,
      String idlPackagePrefix,
      String repoIdPrefix)
      throws IOException {

    GenContext baseCtx =
        setupProject(stubSourceRoot, outputDir, basePkgName, idlPackagePrefix, repoIdPrefix);
    String serviceName = CorbaTypeUtils.toServiceName(serviceIface);
    GenContext ctx = serviceCtx(serviceIface, returnAsDto, baseCtx);
    generateService(serviceIface, serviceName, ctx, "CorbaClient");
    ServiceDtoWriter.writeApplicationProperties(ctx, List.of(serviceName));
  }

  /**
   * 複数のCORBAサービスインターフェースをまとめて単一のSpring Bootプロジェクトに生成 サービスごとに専用のCorbaClient（例:
   * `ItemServiceCorbaClient`）とControllerが生成されます
   *
   * @param services CORBA サービスインターフェースのクラス宣言一覧
   * @param stubSourceRoot idlj によって生成されたスタブソースのルートディレクトリ
   * @param outputDir 生成する Spring Boot プロジェクトの出力ディレクトリ
   * @param basePkgName 生成プロジェクトのベースパッケージ名
   * @param returnAsDto 複雑な戻り値型を DTO に変換するかどうか
   * @param idlPackagePrefix idlj `-pkgPrefix` に渡したパッケージプレフィックス
   * @param repoIdPrefix IDL `#pragma prefix` で設定されたリポジトリIDプレフィックス
   */
  public static void generateAll(
      List<ClassOrInterfaceDeclaration> services,
      Path stubSourceRoot,
      Path outputDir,
      String basePkgName,
      boolean returnAsDto,
      String idlPackagePrefix,
      String repoIdPrefix)
      throws IOException {

    GenContext baseCtx =
        setupProject(stubSourceRoot, outputDir, basePkgName, idlPackagePrefix, repoIdPrefix);
    List<String> serviceNames = new ArrayList<>();
    for (ClassOrInterfaceDeclaration serviceIface : services) {
      String serviceName = CorbaTypeUtils.toServiceName(serviceIface);
      serviceNames.add(serviceName);
      GenContext ctx = serviceCtx(serviceIface, returnAsDto, baseCtx);
      generateService(serviceIface, serviceName, ctx, serviceName + "CorbaClient");
    }
    ServiceDtoWriter.writeApplicationProperties(baseCtx, serviceNames);
  }

  /**
   * プロジェクトの共通部分（ディレクトリ・ビルドファイル・共有クラス・スタブ・構造体DTO/Mapper）を生成し、ベースの`GenContext`を返す
   *
   * <p>返す`GenContext`は`servicePackage=""`、`returnAsDto=false`のプレースホルダー値を持つ。
   * サービス固有のコンテキストは`GenContext#withService`で生成すること。
   *
   * @param stubSourceRoot スタブのルートディレクトリ
   * @param outputDir Spring Boot プロジェクトの出力先
   * @param basePkgName ベースパッケージ名
   * @param idlPackagePrefix idlj `-pkgPrefix` に渡したパッケージプレフィックス
   * @param repoIdPrefix IDL `#pragma prefix` で設定されたリポジトリIDプレフィックス
   * @return ディレクトリ・IDL設定を持つベースの`GenContext`
   */
  private static GenContext setupProject(
      Path stubSourceRoot,
      Path outputDir,
      String basePkgName,
      String idlPackagePrefix,
      String repoIdPrefix)
      throws IOException {
    Path javaDir = outputDir.resolve("src/main/java");
    Path pkgBaseDir = javaDir.resolve(basePkgName.replace('.', '/'));
    Path controllerDir = pkgBaseDir.resolve("controller");
    Path corbaDir = pkgBaseDir.resolve("corba");

    GenContext ctx =
        new GenContext(
            basePkgName,
            "",
            false,
            idlPackagePrefix,
            repoIdPrefix,
            javaDir,
            controllerDir,
            corbaDir,
            stubSourceRoot);

    Files.createDirectories(controllerDir);
    Files.createDirectories(ctx.dtoDir());
    Files.createDirectories(ctx.mapperDir());
    Files.createDirectories(corbaDir);
    Files.createDirectories(ctx.resourcesDir());

    ProjectScaffolder.writeBuildGradle(outputDir);
    ProjectScaffolder.writeApplicationClass(basePkgName, pkgBaseDir);
    ProjectScaffolder.writeServletClass(basePkgName, pkgBaseDir);
    StubHandler.copyIdlStubSources(stubSourceRoot, javaDir);
    AnyTypeWriter.writeAnyValueDto(ctx);
    // 構造体一覧を先に収集してAnyMapperのtk_struct生成にも渡す
    List<ClassOrInterfaceDeclaration> structClasses =
        StubHandler.collectStructClasses(stubSourceRoot);
    AnyTypeWriter.writeAnyMapper(ctx, structClasses);
    StringMapperWriter.write(ctx);

    for (ClassOrInterfaceDeclaration structClass : structClasses) {
      DtoGenerator.generate(structClass, ctx);
      MapperGenerator.generate(structClass, ctx);
    }

    return ctx;
  }

  /**
   * ベースの`GenContext`にサービス固有の設定を適用した`GenContext`を返す
   *
   * @param serviceIface サービスインターフェースのクラス宣言
   * @param returnAsDto 複雑な戻り値型をDTOに変換するかどうか
   * @param baseCtx `#setupProject` が返したベースの`GenContext`
   * @return サービス固有フィールドを持つ`GenContext`
   */
  private static GenContext serviceCtx(
      ClassOrInterfaceDeclaration serviceIface, boolean returnAsDto, GenContext baseCtx) {
    String servicePackage =
        serviceIface
            .findCompilationUnit()
            .flatMap(CompilationUnit::getPackageDeclaration)
            .map(PackageDeclaration::getNameAsString)
            .orElse("");
    return baseCtx.withService(servicePackage, returnAsDto);
  }

  /**
   * サービス固有ファイル（RequestDto・ResponseDto・CorbaClient・Controller）を生成
   *
   * @param serviceIface サービスインターフェースのクラス宣言
   * @param serviceName サービス名（"Operations" サフィックスを除いたもの）
   * @param ctx コード生成コンテキスト
   * @param clientClassName 生成する CorbaClient クラスの名前（例: "CorbaClient", "ItemServiceCorbaClient"）
   */
  private static void generateService(
      ClassOrInterfaceDeclaration serviceIface,
      String serviceName,
      GenContext ctx,
      String clientClassName)
      throws IOException {

    List<MethodDeclaration> methods = serviceIface.getMethods();
    // メソッドがないサービスはクライアントもコントローラーも生成しない
    if (methods.isEmpty()) return;
    for (MethodDeclaration method : methods) {
      ServiceDtoWriter.generateRequestDto(method, ctx);
      ServiceDtoWriter.generateRpcResponse(method, ctx);
    }

    CorbaClientWriter.write(serviceName, methods, clientClassName, ctx);
    ControllerWriter.write(serviceName, methods, clientClassName, ctx);
  }
}
