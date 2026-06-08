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
 * CORBA サービスを REST エンドポイントとして公開する Spring Boot プロジェクト（Gradle）を生成するユーティリティ
 *
 * <p>主な生成フロー:
 *
 * <ol>
 *   <li>{@link #setupProject} でディレクトリ・共通ファイル・スタブコピー・構造体 DTO/Mapper を出力
 *   <li>{@link #generateService} でサービスごとの RequestDto/ResponseDto・CorbaClient・Controller を出力
 *   <li>{@link ServiceDtoWriter#writeApplicationProperties} で IOR 設定ファイルを出力
 * </ol>
 */
public class SpringBootProjectGenerator {

  /**
   * 単一の CORBA サービスインターフェースから Spring Boot プロジェクトを生成
   *
   * @param serviceIface CORBA サービスインターフェースのクラス宣言
   * @param stubSourceRoot idlj によって生成されたスタブソースのルートディレクトリ
   * @param outputDir 生成する Spring Boot プロジェクトの出力ディレクトリ
   * @param basePkgName 生成プロジェクトのベースパッケージ名
   * @param returnAsDto 複雑な戻り値型を DTO に変換するかどうか
   */
  public static void generate(
      ClassOrInterfaceDeclaration serviceIface,
      Path stubSourceRoot,
      Path outputDir,
      String basePkgName,
      boolean returnAsDto)
      throws IOException {

    ProjectLayout layout = setupProject(stubSourceRoot, outputDir, basePkgName);
    String serviceName = CorbaTypeUtils.toServiceName(serviceIface);
    generateService(serviceIface, serviceName, layout, basePkgName, returnAsDto, "CorbaClient");
    ServiceDtoWriter.writeApplicationProperties(outputDir, List.of(serviceName));
  }

  /**
   * 複数の CORBA サービスインターフェースをまとめて単一の Spring Boot プロジェクトに生成 サービスごとに専用の CorbaClient（例: {@code
   * ItemServiceCorbaClient}）と Controller が生成されます
   *
   * @param services CORBA サービスインターフェースのクラス宣言一覧
   * @param stubSourceRoot idlj によって生成されたスタブソースのルートディレクトリ
   * @param outputDir 生成する Spring Boot プロジェクトの出力ディレクトリ
   * @param basePkgName 生成プロジェクトのベースパッケージ名
   * @param returnAsDto 複雑な戻り値型を DTO に変換するかどうか
   */
  public static void generateAll(
      List<ClassOrInterfaceDeclaration> services,
      Path stubSourceRoot,
      Path outputDir,
      String basePkgName,
      boolean returnAsDto)
      throws IOException {

    ProjectLayout layout = setupProject(stubSourceRoot, outputDir, basePkgName);
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
   * プロジェクトの共通部分（ディレクトリ・ビルドファイル・共有クラス・スタブ・構造体 DTO/Mapper）を生成し、出力先のレイアウト情報を返す
   *
   * @param stubSourceRoot スタブのルートディレクトリ
   * @param outputDir Spring Boot プロジェクトの出力先
   * @param basePkgName ベースパッケージ名
   * @return 生成先ディレクトリ群を保持するレイアウト情報
   */
  private static ProjectLayout setupProject(Path stubSourceRoot, Path outputDir, String basePkgName)
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
    AnyTypeWriter.writeAnyMapper(mapperDir, basePkgName);
    StringMapperWriter.write(mapperDir, basePkgName);

    for (ClassOrInterfaceDeclaration structClass :
        StubHandler.collectStructClasses(stubSourceRoot)) {
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
      boolean hasOutParams =
          method.getParameters().stream()
              .anyMatch(p -> CorbaTypeUtils.isHolderType(p.getType().asString()));
      if (hasOutParams) {
        ServiceDtoWriter.generateResponseDto(
            method,
            layout.javaDir,
            basePkgName,
            returnAsDto,
            servicePackage,
            layout.stubSourceRoot);
      }
      // XxxRpcResult / XxxRpcResponse を全メソッドで生成（ResponseDto がある場合はその後に生成）
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
