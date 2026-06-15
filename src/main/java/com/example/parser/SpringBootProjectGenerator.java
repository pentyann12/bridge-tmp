package com.example.parser;

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
 * <ol>
 *   1. {@link #setupProject}でディレクトリ・共通ファイル・スタブコピー・構造体DTO/Mapperを出力 2. {@link
 *   #generateService}でサービスごとのRequestDto/ResponseDto・CorbaClient・Controllerを出力 3. {@link
 *   ServiceDtoWriter#writeApplicationProperties}でIOR設定ファイルを出力
 * </ol>
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

    ProjectLayout layout =
        setupProject(stubSourceRoot, outputDir, basePkgName, idlPackagePrefix, repoIdPrefix);
    String serviceName = CorbaTypeUtils.toServiceName(serviceIface);
    generateService(serviceIface, serviceName, layout, basePkgName, returnAsDto, "CorbaClient");
    ServiceDtoWriter.writeApplicationProperties(outputDir, List.of(serviceName));
  }

  /**
   * 複数のCORBAサービスインターフェースをまとめて単一のSpring Bootプロジェクトに生成 サービスごとに専用のCorbaClient（例: {@code
   * ItemServiceCorbaClient}）とControllerが生成されます
   *
   * @param services CORBA サービスインターフェースのクラス宣言一覧
   * @param stubSourceRoot idlj によって生成されたスタブソースのルートディレクトリ
   * @param outputDir 生成する Spring Boot プロジェクトの出力ディレクトリ
   * @param basePkgName 生成プロジェクトのベースパッケージ名
   * @param returnAsDto 複雑な戻り値型を DTO に変換するかどうか
   * @param idlPackagePrefix idlj {@code -pkgPrefix} に渡したパッケージプレフィックス
   * @param repoIdPrefix IDL {@code #pragma prefix} で設定されたリポジトリIDプレフィックス
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

    ProjectLayout layout =
        setupProject(stubSourceRoot, outputDir, basePkgName, idlPackagePrefix, repoIdPrefix);
    List<String> serviceNames = new ArrayList<>();
    for (ClassOrInterfaceDeclaration serviceIface : services) {
      String serviceName = CorbaTypeUtils.toServiceName(serviceIface);
      serviceNames.add(serviceName);
      generateService(
          serviceIface, serviceName, layout, basePkgName, returnAsDto, serviceName + "CorbaClient");
    }
    ServiceDtoWriter.writeApplicationProperties(outputDir, serviceNames);
  }

  /**
   * プロジェクトの共通部分（ディレクトリ・ビルドファイル・共有クラス・スタブ・構造体DTO/Mapper）を生成し、出力先のレイアウト情報を返す
   *
   * @param stubSourceRoot スタブのルートディレクトリ
   * @param outputDir Spring Boot プロジェクトの出力先
   * @param basePkgName ベースパッケージ名
   * @param idlPackagePrefix idlj {@code -pkgPrefix} に渡したパッケージプレフィックス
   * @param repoIdPrefix IDL {@code #pragma prefix} で設定されたリポジトリIDプレフィックス
   * @return 生成先ディレクトリ群を保持するレイアウト情報
   */
  private static ProjectLayout setupProject(
      Path stubSourceRoot,
      Path outputDir,
      String basePkgName,
      String idlPackagePrefix,
      String repoIdPrefix)
      throws IOException {
    Path javaDir = outputDir.resolve("src/main/java");
    Path pkgBaseDir = javaDir.resolve(basePkgName.replace('.', '/'));
    Path controllerDir = pkgBaseDir.resolve("controller");
    Path dtoDir = pkgBaseDir.resolve("dto");
    Path mapperDir = pkgBaseDir.resolve("mapper");
    Path corbaDir = pkgBaseDir.resolve("corba");

    Files.createDirectories(controllerDir);
    Files.createDirectories(dtoDir);
    Files.createDirectories(mapperDir);
    Files.createDirectories(corbaDir);
    Files.createDirectories(outputDir.resolve("src/main/resources"));

    ProjectScaffolder.writeBuildGradle(outputDir);
    ProjectScaffolder.writeApplicationClass(basePkgName, pkgBaseDir);
    ProjectScaffolder.writeServletClass(basePkgName, pkgBaseDir);
    StubHandler.copyIdlStubSources(stubSourceRoot, javaDir);
    AnyTypeWriter.writeAnyValueDto(dtoDir, basePkgName);
    // 構造体一覧を先に収集してAnyMapperのtk_struct生成にも渡す
    List<ClassOrInterfaceDeclaration> structClasses =
        StubHandler.collectStructClasses(stubSourceRoot);
    AnyTypeWriter.writeAnyMapper(
        mapperDir, basePkgName, structClasses, idlPackagePrefix, repoIdPrefix);
    StringMapperWriter.write(mapperDir, basePkgName);

    for (ClassOrInterfaceDeclaration structClass : structClasses) {
      DtoGenerator.generate(structClass, javaDir, basePkgName);
      MapperGenerator.generate(structClass, javaDir, basePkgName);
    }

    return new ProjectLayout(javaDir, controllerDir, corbaDir, stubSourceRoot);
  }

  /**
   * サービス固有ファイル（RequestDto・ResponseDto・CorbaClient・Controller）を生成
   *
   * @param serviceIface サービスインターフェースのクラス宣言
   * @param serviceName サービス名（"Operations" サフィックスを除いたもの）
   * @param layout 出力先ディレクトリ群
   * @param basePkgName ベースパッケージ名
   * @param returnAsDto 複雑な戻り値型を DTO に変換するかどうか
   * @param clientClassName 生成する CorbaClient クラスの名前（例: "CorbaClient", "ItemServiceCorbaClient"）
   */
  private static void generateService(
      ClassOrInterfaceDeclaration serviceIface,
      String serviceName,
      ProjectLayout layout,
      String basePkgName,
      boolean returnAsDto,
      String clientClassName)
      throws IOException {

    String servicePackage =
        serviceIface
            .findCompilationUnit()
            .flatMap(CompilationUnit::getPackageDeclaration)
            .map(PackageDeclaration::getNameAsString)
            .orElse("");

    List<MethodDeclaration> methods = serviceIface.getMethods();
    // メソッドがないサービスはクライアントもコントローラーも生成しない
    if (methods.isEmpty()) return;
    for (MethodDeclaration method : methods) {
      ServiceDtoWriter.generateRequestDto(method, layout.javaDir, basePkgName);
      ServiceDtoWriter.generateRpcResponse(
          method, layout.javaDir, basePkgName, returnAsDto, servicePackage, layout.stubSourceRoot);
    }

    CorbaClientWriter.write(
        serviceName, servicePackage, methods, layout.corbaDir, basePkgName, clientClassName);
    ControllerWriter.write(
        serviceName,
        servicePackage,
        methods,
        layout.controllerDir,
        basePkgName,
        returnAsDto,
        layout.stubSourceRoot,
        clientClassName);
  }
}
