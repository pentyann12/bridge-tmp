package com.example.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;

/**
 * CORBA サービスを REST エンドポイントとして公開する Spring Boot プロジェクト（Gradle）を生成するユーティリティ
 *
 * <p>主な生成フロー:
 *
 * <ol>
 *   <li>{@link #setupProject} でディレクトリ・共通ファイル・スタブコピー・構造体 DTO/Mapper を出力
 *   <li>{@link #generateService} でサービスごとの RequestDto/ResponseDto・CorbaClient・Controller を出力
 *   <li>{@link #writeApplicationProperties} で IOR 設定ファイルを出力
 * </ol>
 */
public class SpringBootProjectGenerator {

  // ─────────────────────────────── Public API ──────────────────────────────

  /**
   * 単一の CORBA サービスインターフェースから Spring Boot プロジェクトを生成します
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
    String serviceName = toServiceName(serviceIface);
    generateService(serviceIface, serviceName, layout, basePkgName, returnAsDto, "CorbaClient");
    writeApplicationProperties(outputDir, List.of(serviceName));
  }

  /**
   * 複数の CORBA サービスインターフェースをまとめて単一の Spring Boot プロジェクトに生成します サービスごとに専用の CorbaClient（例: {@code
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
      String serviceName = toServiceName(serviceIface);
      serviceNames.add(serviceName);
      generateService(
          serviceIface, serviceName, layout, basePkgName, returnAsDto, serviceName + "CorbaClient");
    }
    writeApplicationProperties(outputDir, serviceNames);
  }

  // ─────────────────────────── Project scaffold ─────────────────────────────

  /**
   * プロジェクトの共通部分（ディレクトリ・ビルドファイル・共有クラス・スタブ・構造体 DTO/Mapper）を生成し、 
   * 出力先のレイアウト情報を返します
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

    writeBuildGradle(outputDir);
    writeApplicationClass(basePkgName, pkgBaseDir);
    writeServletClass(basePkgName, pkgBaseDir);
    copyIdlStubSources(stubSourceRoot, javaDir);
    writeAnyValueDto(dtoDir, basePkgName);
    writeAnyMapper(mapperDir, basePkgName);

    for (ClassOrInterfaceDeclaration structClass : collectStructClasses(stubSourceRoot)) {
      DtoGenerator.generate(structClass, javaDir, basePkgName);
      MapperGenerator.generate(structClass, javaDir, basePkgName);
    }

    return new ProjectLayout(javaDir, controllerDir, corbaDir, stubSourceRoot);
  }

  /**
   * サービス固有ファイル（RequestDto・ResponseDto・CorbaClient・Controller）を生成します
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
    for (MethodDeclaration method : methods) {
      generateRequestDto(method, layout.javaDir, basePkgName);
      boolean hasOutParams =
          method.getParameters().stream().anyMatch(p -> isHolderType(p.getType().asString()));
      if (hasOutParams) {
        generateResponseDto(
            method,
            layout.javaDir,
            basePkgName,
            returnAsDto,
            servicePackage,
            layout.stubSourceRoot);
      }
    }

    writeCorbaClient(
        serviceName, servicePackage, methods, layout.corbaDir, basePkgName, clientClassName);
    writeController(
        serviceName,
        servicePackage,
        methods,
        layout.controllerDir,
        basePkgName,
        returnAsDto,
        layout.stubSourceRoot,
        clientClassName);
  }

  /** 出力先ディレクトリ群を保持する不変ホルダー */
  private static final class ProjectLayout {
    final Path javaDir;
    final Path controllerDir;
    final Path corbaDir;

    /** Holder の value 型解決でスタブファイルを参照するために保持する */
    final Path stubSourceRoot;

    ProjectLayout(Path javaDir, Path controllerDir, Path corbaDir, Path stubSourceRoot) {
      this.javaDir = javaDir;
      this.controllerDir = controllerDir;
      this.corbaDir = corbaDir;
      this.stubSourceRoot = stubSourceRoot;
    }
  }

  // MARK: 雛形ファイル出力

  /** build.gradleを出力する
   * 
   * @param outputDir 出力先ディレクトリ
   */
  private static void writeBuildGradle(Path outputDir) throws IOException {
    String content =
        """
        plugins {
          id 'java'
          id 'war'
          id 'org.springframework.boot' version '4.0.6'
          id 'io.spring.dependency-management' version '1.1.7'
        }

        group = 'generated'
        version = '0.0.1-SNAPSHOT'

        java {
          toolchain {
            languageVersion = JavaLanguageVersion.of(21)
          }
        }

        repositories {
          mavenCentral()
        }

        dependencies {
          implementation 'org.springframework.boot:spring-boot-starter-web'
          implementation 'com.fasterxml.jackson.core:jackson-databind:2.15.2'
          implementation 'org.glassfish.corba:glassfish-corba-orb:4.2.5'
          implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
        }
        """;
    Files.writeString(
        outputDir.resolve("build.gradle"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * Spring Boot エントリポイントクラスを出力する
   * 
   * @param basePkgName 基礎パッケージ名
   * @param pkgBaseDir 出力先ディレクトリ
   */
  private static void writeApplicationClass(String basePkgName, Path pkgBaseDir)
      throws IOException {
    String content =
        """
        package %s;

        import org.springframework.boot.SpringApplication;
        import org.springframework.boot.autoconfigure.SpringBootApplication;

        @SpringBootApplication
        public class Application {
          public static void main(String[] args) {
            System.setProperty(
              "org.glassfish.gmbal.no.multipleUpperBoundsException",
              "true");
            SpringApplication.run(Application.class, args);
          }
        }
        """
            .formatted(basePkgName);
    Files.writeString(
        pkgBaseDir.resolve("Application.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * ServletInitializer を出力する
   * 
   * @param basePkgName 基礎パッケージ名
   * @param pkgBaseDir 出力先ディレクトリ
   */
  private static void writeServletClass(String basePkgName, Path pkgBaseDir) throws IOException {
    String content =
        """
        package %s;

        import org.springframework.boot.builder.SpringApplicationBuilder;
        import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

        public class ServletInitializer extends SpringBootServletInitializer {
          @Override
          protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
            return application.sources(Application.class);
          }
        }
        """
            .formatted(basePkgName);
    Files.writeString(
        pkgBaseDir.resolve("ServletInitializer.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * スタブを生成先プロジェクトへコピーする
   *
   * @param stubSourceRoot スタブディレクトリ
   * @param javaDir コピー先ディレクトリ
   */
  private static void copyIdlStubSources(Path stubSourceRoot, Path javaDir) throws IOException {
    Files.walk(stubSourceRoot)
        .filter(Files::isRegularFile)
        .filter(p -> p.toString().endsWith(".java"))
        .forEach(
            p -> {
              try {
                Path target = javaDir.resolve(stubSourceRoot.relativize(p));
                Files.createDirectories(target.getParent());
                Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
  }

  /**
   * AnyValueクラスを出力する
   *
   * @param dtoDir 出力先ディレクトリ
   * @param basePkgName ベースパッケージ名
   */
  private static void writeAnyValueDto(Path dtoDir, String basePkgName) throws IOException {
    String content =
        """
        package %s.dto;

        public class AnyValue {
          public String type;
          public Object value;
          public AnyValue() {}
          public AnyValue(String type, Object value) {
            this.type = type;
            this.value = value;
          }
        }
        """
            .formatted(basePkgName);
    Files.writeString(
        dtoDir.resolve("AnyValue.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * CORBA.Any <=> AnyValue 相互変換Mapperを出力する
   *
   * @param mapperDir Mapperディレクトリ
   * @param basePkgName ベースパッケージ名
   */
  private static void writeAnyMapper(Path mapperDir, String basePkgName) throws IOException {
    String pkg = basePkgName + ".mapper";
    String content =
        """
        package %s;

        import org.omg.CORBA.Any;
        import org.omg.CORBA.ORB;
        import org.omg.CORBA.TCKind;
        import org.omg.CORBA.Object;
        import %s.dto.AnyValue;

        public final class AnyMapper {
          private AnyMapper() {}

          public static Any toAny(AnyValue dto) {
            if (dto == null) return null;
            Any any = ORB.init(new String[0], null).create_any();
            if (dto.type == null) return any;
            switch (dto.type) {
              case "boolean": any.insert_boolean((Boolean) dto.value); break;
              case "short": any.insert_short(((Number) dto.value).shortValue()); break;
              case "long": any.insert_long(((Number) dto.value).intValue()); break;
              case "float": any.insert_float(((Number) dto.value).floatValue()); break;
              case "double": any.insert_double(((Number) dto.value).doubleValue()); break;
              case "string": any.insert_string(String.valueOf(dto.value)); break;
              case "any": any.insert_any(toAny((AnyValue) dto.value)); break;
              default: any.insert_Object((Object) dto.value); break;
            }
            return any;
          }

          public static AnyValue toAnyValue(Any any) {
            if (any == null) return null;
            TCKind kind = any.type().kind();
            if (kind.equals(TCKind.tk_null) || kind.equals(TCKind.tk_void)) return null;
            if (kind.equals(TCKind.tk_boolean)) return new AnyValue("boolean", any.extract_boolean());
            else if (kind.equals(TCKind.tk_short)) return new AnyValue("short", any.extract_short());
            else if (kind.equals(TCKind.tk_ushort)) return new AnyValue("short", any.extract_ushort());
            else if (kind.equals(TCKind.tk_long)) return new AnyValue("int", any.extract_long());
            else if (kind.equals(TCKind.tk_ulong)) return new AnyValue("int", any.extract_ulong());
            else if (kind.equals(TCKind.tk_longlong)) return new AnyValue("long", any.extract_longlong());
            else if (kind.equals(TCKind.tk_ulonglong)) return new AnyValue("long", any.extract_ulonglong());
            else if (kind.equals(TCKind.tk_float)) return new AnyValue("float", any.extract_float());
            else if (kind.equals(TCKind.tk_double)) return new AnyValue("double", any.extract_double());
            else if (kind.equals(TCKind.tk_char)) return new AnyValue("char", any.extract_char());
            else if (kind.equals(TCKind.tk_wchar)) return new AnyValue("char", any.extract_wchar());
            else if (kind.equals(TCKind.tk_octet)) return new AnyValue("byte", any.extract_octet());
            else if (kind.equals(TCKind.tk_string)) return new AnyValue("string", any.extract_string());
            else if (kind.equals(TCKind.tk_any)) return new AnyValue("any", toAnyValue(any.extract_any()));
            else return new AnyValue("object", any.extract_Object());
          }
        }
        """
            .formatted(pkg, basePkgName);
    Files.writeString(
        mapperDir.resolve("AnyMapper.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  // MARK: 動的ファイル生成
  
  /**
   * CORBA サービス呼び出しをラップする CorbaClient クラスを生成します
   *
   * @param serviceName サービス名（例: "ItemService"）
   * @param servicePackage サービスが属するパッケージ
   * @param methods サービスインターフェースのメソッド一覧
   * @param corbaDir 出力ディレクトリ
   * @param basePackage ベースパッケージ名
   * @param clientClassName 生成するクラスの名前（例: "CorbaClient", "ItemServiceCorbaClient"）
   */
  private static void writeCorbaClient(
      String serviceName,
      String servicePackage,
      List<MethodDeclaration> methods,
      Path corbaDir,
      String basePackage,
      String clientClassName)
      throws IOException {

    StringBuilder sb = new StringBuilder();
    sb.append("package ").append(basePackage).append(".corba;\n\n");
    sb.append("import org.omg.CORBA.ORB;\n");
    sb.append("import org.omg.CORBA.Object;\n");
    sb.append("import org.springframework.beans.factory.annotation.Value;\n");
    sb.append("import org.springframework.stereotype.Component;\n");
    if (!servicePackage.isEmpty()) {
      sb.append("import ").append(servicePackage).append(".").append(serviceName).append(";\n");
      sb.append("import ")
          .append(servicePackage)
          .append(".")
          .append(serviceName)
          .append("Helper;\n");
    }
    sb.append("\n@Component\npublic class ").append(clientClassName).append(" {\n");
    sb.append("    private final ").append(serviceName).append(" service;\n\n");
    // IOR は application.yml の corba.<serviceName>.ior から注入する
    sb.append("    public ")
        .append(clientClassName)
        .append("(@Value(\"${corba.")
        .append(serviceName)
        .append(".ior}\") String ior) {\n");
    sb.append("        ORB orb = ORB.init(new String[0], null);\n");
    sb.append("        org.omg.CORBA.Object obj = orb.string_to_object(ior);\n");
    sb.append("        this.service = ").append(serviceName).append("Helper.narrow(obj);\n");
    sb.append("    }\n\n");

    for (MethodDeclaration method : methods) {
      String returnType = method.getType().asString();
      sb.append("    public ")
          .append(resolveTypeFQN(returnType, method, servicePackage))
          .append(" ")
          .append(method.getNameAsString())
          .append("(");
      for (int i = 0; i < method.getParameters().size(); i++) {
        Parameter p = method.getParameters().get(i);
        if (i > 0) sb.append(", ");
        sb.append(resolveTypeFQN(p.getType().asString(), method, servicePackage))
            .append(" ")
            .append(p.getNameAsString());
      }
      sb.append(") {\n");
      // void メソッドは return なし
      String callPrefix =
          "void".equals(returnType) ? "        service." : "        return service.";
      sb.append(callPrefix).append(method.getNameAsString()).append("(");
      sb.append(
          method.getParameters().stream()
              .map(Parameter::getNameAsString)
              .collect(Collectors.joining(", ")));
      sb.append(");\n    }\n\n");
    }
    sb.append("}\n");

    Files.writeString(
        corbaDir.resolve(clientClassName + ".java"),
        sb.toString(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * Spring MVC コントローラークラスを生成します メソッドごとに {@link #appendControllerMethod} を呼び出してコードを組み立てます
   *
   * @param serviceName サービス名
   * @param servicePackage サービスが属するパッケージ
   * @param methods サービスインターフェースのメソッド一覧
   * @param controllerDir 出力ディレクトリ
   * @param basePackage ベースパッケージ名
   * @param returnAsDto 複雑な戻り値型を DTO に変換するかどうか
   * @param stubRoot スタブのルートディレクトリ（Holder value 型解決に使用）
   * @param clientClassName CorbaClient クラスの名前
   */
  private static void writeController(
      String serviceName,
      String servicePackage,
      List<MethodDeclaration> methods,
      Path controllerDir,
      String basePackage,
      boolean returnAsDto,
      Path stubRoot,
      String clientClassName)
      throws IOException {

    String controllerName = serviceName + "Controller";
    String dtoPackage = basePackage + ".dto";
    String clientFqn = basePackage + ".corba." + clientClassName;
    StringBuilder methodsCode = new StringBuilder();

    for (MethodDeclaration method : methods) {
      appendControllerMethod(
          methodsCode, method, basePackage, dtoPackage, servicePackage, returnAsDto, stubRoot);
    }

    // フィールドとコンストラクターインジェクション
    String fieldAndCtor =
        "    private final "
            + clientFqn
            + " client;\n\n"
            + "    public "
            + controllerName
            + "("
            + clientFqn
            + " client) {\n"
            + "        this.client = client;\n"
            + "    }\n";

    String controller =
        """
        package %s.controller;

        import org.springframework.web.bind.annotation.RestController;
        import org.springframework.web.bind.annotation.RequestMapping;
        import org.springframework.web.bind.annotation.PostMapping;
        import org.springframework.web.bind.annotation.RequestBody;

        @RestController
        @RequestMapping("/")
        public class %s {
        %s
        %s
        }
        """
            .formatted(basePackage, controllerName, fieldAndCtor, methodsCode);

    Files.writeString(
        controllerDir.resolve(controllerName + ".java"),
        controller,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * コントローラーメソッド1つ分のコードを {@code sb} に追記します
   *
   * <p>out引数（Holder型）の有無で挙動が変わります:
   *
   * <ul>
   *   <li>out引数なし: 戻り値を直接返す通常の POST メソッドを生成
   *   <li>out引数あり: 空の Holder を生成して CORBA を呼び出し、 戻り値と out 引数をまとめた ResponseDto を返すメソッドを生成
   * </ul>
   */
  private static void appendControllerMethod(
      StringBuilder sb,
      MethodDeclaration method,
      String basePackage,
      String dtoPackage,
      String servicePackage,
      boolean returnAsDto,
      Path stubRoot) {

    String methodName = method.getNameAsString();
    String returnType = method.getType().asString();
    boolean hasOutParams =
        method.getParameters().stream().anyMatch(p -> isHolderType(p.getType().asString()));
    boolean hasInParams =
        method.getParameters().stream().anyMatch(p -> !isHolderType(p.getType().asString()));
    String requestDtoType = dtoPackage + "." + capitalize(methodName) + "RequestDto";

    // シグネチャ
    sb.append("    @PostMapping(\"/").append(methodName).append("\")\n");
    sb.append("    public ")
        .append(
            resolveControllerReturnType(
                method, basePackage, dtoPackage, servicePackage, returnAsDto, hasOutParams))
        .append(" ")
        .append(methodName)
        .append("(");
    if (hasInParams) {
      sb.append("@RequestBody ").append(requestDtoType).append(" req");
    }
    sb.append(") {\n");

    // 引数変換（in引数: DTO→CORBA変換、out引数: 空 Holder を生成）
    List<String> argNames = new ArrayList<>();
    for (int i = 0; i < method.getParameters().size(); i++) {
      Parameter param = method.getParameters().get(i);
      String argName = "arg" + i;
      argNames.add(argName);
      if (isHolderType(param.getType().asString())) {
        // out引数: 呼び出し前に空 Holder を生成し、呼び出し後に .value を取り出す
        String holderFqn = resolveTypeFQN(param.getType().asString(), method, servicePackage);
        sb.append("        ")
            .append(holderFqn)
            .append(" ")
            .append(argName)
            .append(" = new ")
            .append(holderFqn)
            .append("();\n");
      } else {
        appendInArgConversion(sb, param, argName, "req", basePackage, servicePackage, method);
      }
    }

    String callArgs = String.join(", ", argNames);

    if (hasOutParams) {
      // CORBA 呼び出し後に ResponseDto を構築して返す
      if (!"void".equals(returnType)) {
        sb.append("        ")
            .append(resolveTypeFQN(returnType, method, servicePackage))
            .append(" callResult = client.")
            .append(methodName)
            .append("(")
            .append(callArgs)
            .append(");\n");
      } else {
        sb.append("        client.").append(methodName).append("(").append(callArgs).append(");\n");
      }
      appendOutParamResponseDto(
          sb,
          method,
          returnType,
          argNames,
          dtoPackage,
          basePackage,
          servicePackage,
          returnAsDto,
          stubRoot);

    } else if ("void".equals(returnType)) {
      sb.append("        client.").append(methodName).append("(").append(callArgs).append(");\n");
      sb.append("        return java.util.Collections.singletonMap(\"status\", \"ok\");\n");

    } else {
      appendDirectReturn(
          sb, method, returnType, callArgs, basePackage, dtoPackage, servicePackage, returnAsDto);
    }

    sb.append("    }\n\n");
  }

  /**
   * out引数を持つメソッドのレスポンスDTO構築コードを追記します CORBA 呼び出し後の {@code callResult} と各 {@code Holder.value} を
   * ResponseDto フィールドに詰めて返します
   */
  private static void appendOutParamResponseDto(
      StringBuilder sb,
      MethodDeclaration method,
      String returnType,
      List<String> argNames,
      String dtoPackage,
      String basePackage,
      String servicePackage,
      boolean returnAsDto,
      Path stubRoot) {

    String responseDtoType =
        dtoPackage + "." + capitalize(method.getNameAsString()) + "ResponseDto";
    sb.append("        ")
        .append(responseDtoType)
        .append(" res = new ")
        .append(responseDtoType)
        .append("();\n");

    if (!"void".equals(returnType)) {
      sb.append("        res.returnValue = ")
          .append(corbaToDtoExpr("callResult", returnType, basePackage, dtoPackage, returnAsDto))
          .append(";\n");
    }

    for (int i = 0; i < method.getParameters().size(); i++) {
      Parameter param = method.getParameters().get(i);
      if (!isHolderType(param.getType().asString())) continue;
      String valueType =
          resolveHolderValueType(param.getType().asString(), method, servicePackage, stubRoot);
      sb.append("        res.")
          .append(param.getNameAsString())
          .append(" = ")
          .append(
              corbaToDtoExpr(
                  argNames.get(i) + ".value", valueType, basePackage, dtoPackage, returnAsDto))
          .append(";\n");
    }

    sb.append("        return res;\n");
  }

  /** out引数なし・非void メソッドの return 文を追記します 戻り型に応じて primitive/String/Any/独自型・配列を使い分けます */
  private static void appendDirectReturn(
      StringBuilder sb,
      MethodDeclaration method,
      String returnType,
      String callArgs,
      String basePackage,
      String dtoPackage,
      String servicePackage,
      boolean returnAsDto) {

    String methodName = method.getNameAsString();

    if (isPrimitiveType(returnType)) {
      sb.append("        return client.")
          .append(methodName)
          .append("(")
          .append(callArgs)
          .append(");\n");
    } else if ("org.omg.CORBA.Any".equals(returnType)) {
      if (returnAsDto) {
        sb.append("        return ")
            .append(basePackage)
            .append(".mapper.AnyMapper.toAnyValue(client.")
            .append(methodName)
            .append("(")
            .append(callArgs)
            .append("));\n");
      } else {
        sb.append("        return client.")
            .append(methodName)
            .append("(")
            .append(callArgs)
            .append(");\n");
      }
    } else {
      // 独自型または配列: result 変数に受けてから変換する
      sb.append("        ")
          .append(resolveTypeFQN(returnType, method, servicePackage))
          .append(" result = client.")
          .append(methodName)
          .append("(")
          .append(callArgs)
          .append(");\n");
      sb.append("        return ")
          .append(corbaToDtoExpr("result", returnType, basePackage, dtoPackage, returnAsDto))
          .append(";\n");
    }
  }

  // ──────────────────────────── DTO generation ──────────────────────────────

  /**
   * CORBA サービスメソッドの in パラメーター（Holder型を除く）をフィールドとして持つ リクエスト DTO を出力します
   *
   * @param method サービスメソッドの宣言
   * @param outputRoot Java ソースのルートディレクトリ
   * @param basePkgName ベースパッケージ名
   */
  private static void generateRequestDto(
      MethodDeclaration method, Path outputRoot, String basePkgName) throws IOException {
    String dtoPkgName = basePkgName + ".dto";
    TypeSpec.Builder typeBuilder =
        TypeSpec.classBuilder(capitalize(method.getNameAsString()) + "RequestDto")
            .addModifiers(Modifier.PUBLIC);

    for (Parameter param : method.getParameters()) {
      if (isHolderType(param.getType().asString())) continue; // out引数はリクエストボディに含めない
      String fieldType = mapToDtoType(basePkgName, param.getType().asString());
      typeBuilder.addField(
          FieldSpec.builder(
                  convToTypeName(fieldType, dtoPkgName), param.getNameAsString(), Modifier.PUBLIC)
              .build());
    }

    JavaFile.builder(dtoPkgName, typeBuilder.build()).build().writeTo(outputRoot);
  }

  /**
   * out引数を持つメソッド専用のレスポンス DTO を出力します 非void戻り値は {@code returnValue} フィールド、out引数はパラメーター名のフィールドになります
   *
   * @param method サービスメソッドの宣言
   * @param outputRoot Java ソースのルートディレクトリ
   * @param basePkgName ベースパッケージ名
   * @param returnAsDto true の場合、複雑な型を DTO 型に変換する
   * @param servicePackage サービスが属するパッケージ（FQN 解決のフォールバック用）
   * @param stubRoot Holder の value 型解決でスタブファイルを参照するために使用
   */
  private static void generateResponseDto(
      MethodDeclaration method,
      Path outputRoot,
      String basePkgName,
      boolean returnAsDto,
      String servicePackage,
      Path stubRoot)
      throws IOException {
    String dtoPkgName = basePkgName + ".dto";
    TypeSpec.Builder typeBuilder =
        TypeSpec.classBuilder(capitalize(method.getNameAsString()) + "ResponseDto")
            .addModifiers(Modifier.PUBLIC);

    String returnType = method.getType().asString();
    if (!"void".equals(returnType)) {
      String fieldType =
          returnAsDto
              ? mapToDtoType(basePkgName, returnType)
              : resolveTypeFQN(returnType, method, servicePackage);
      typeBuilder.addField(
          FieldSpec.builder(convToTypeName(fieldType, dtoPkgName), "returnValue", Modifier.PUBLIC)
              .build());
    }

    for (Parameter param : method.getParameters()) {
      String paramType = param.getType().asString();
      if (!isHolderType(paramType)) continue;
      String valueType = resolveHolderValueType(paramType, method, servicePackage, stubRoot);
      String fieldType =
          returnAsDto
              ? mapToDtoType(basePkgName, valueType)
              : (valueType.contains(".")
                  ? valueType
                  : resolveTypeFQN(valueType, method, servicePackage));
      typeBuilder.addField(
          FieldSpec.builder(
                  convToTypeName(fieldType, dtoPkgName), param.getNameAsString(), Modifier.PUBLIC)
              .build());
    }

    JavaFile.builder(dtoPkgName, typeBuilder.build()).build().writeTo(outputRoot);
  }

  /**
   * application.yml を出力しますサービスが複数の場合は全サービス分の IOR 設定を出力します
   *
   * @param outputDir プロジェクト出力先ディレクトリ
   * @param serviceNames サービス名の一覧
   */
  private static void writeApplicationProperties(Path outputDir, List<String> serviceNames)
      throws IOException {
    StringBuilder sb = new StringBuilder("corba:\n");
    for (String name : serviceNames) {
      sb.append("  ").append(name).append(":\n    ior:\n");
    }
    Files.writeString(
        outputDir.resolve("src/main/resources/application.yml"),
        sb.toString(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  // ─────────────────────────────── Helpers ─────────────────────────────────

  /**
   * スタブから CORBA 構造体クラスの一覧を収集します インターフェース・Helper・Holder・POA・Stub・メソッドを持つクラスは除外します
   *
   * @param stubSourceRoot スタブのルートディレクトリ
   * @return 構造体クラスの一覧
   * @throws IOException スタブの走査に失敗した場合
   */
  private static List<ClassOrInterfaceDeclaration> collectStructClasses(Path stubSourceRoot)
      throws IOException {
    List<ClassOrInterfaceDeclaration> structs = new ArrayList<>();
    Files.walk(stubSourceRoot)
        .filter(Files::isRegularFile)
        .filter(p -> p.toString().endsWith(".java"))
        .forEach(
            p -> {
              try {
                StaticJavaParser.parse(p).findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(SpringBootProjectGenerator::isTargetStruct)
                    .forEach(structs::add);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    return structs;
  }

  /**
   * DTO/Mapper 生成対象の構造体クラスかどうかを判定します インターフェース・各種 idlj 生成クラス（Helper/Holder/POA 等）・メソッドを持つクラスは除外します
   */
  private static boolean isTargetStruct(ClassOrInterfaceDeclaration clazz) {
    if (clazz.isInterface()) return false;
    String name = clazz.getNameAsString();
    return !name.endsWith("Helper")
        && !name.endsWith("Holder")
        && !name.endsWith("Operations")
        && !name.endsWith("POA")
        && !name.endsWith("Stub")
        && !name.startsWith("_")
        && clazz.getMethods().isEmpty();
  }

  /**
   * in引数1つ分の DTO→CORBA 変換コードを {@code sb} に追記します Holder型（out引数）はこのメソッドの対象外です
   *
   * <ul>
   *   <li>プリミティブ/String: {@code req} フィールドをそのまま代入
   *   <li>Any: {@code AnyMapper.toAny()} を経由
   *   <li>配列型: {@code Arrays.stream().map(Mapper::fromDto).toArray()} で変換
   *   <li>独自型: {@code XxxMapper.fromDto()} で変換
   * </ul>
   */
  private static void appendInArgConversion(
      StringBuilder sb,
      Parameter param,
      String argName,
      String handlerName,
      String basePackage,
      String servicePackage,
      MethodDeclaration method) {

    String paramType = param.getType().asString();
    String paramSimple = simpleName(paramType);
    String fieldRef = handlerName + "." + param.getNameAsString();

    if (isPrimitiveType(paramType)) {
      sb.append("        ")
          .append(paramType)
          .append(" ")
          .append(argName)
          .append(" = ")
          .append(fieldRef)
          .append(";\n");
    } else if ("org.omg.CORBA.Any".equals(paramType)) {
      sb.append("        org.omg.CORBA.Any ")
          .append(argName)
          .append(" = ")
          .append(basePackage)
          .append(".mapper.AnyMapper.toAny(")
          .append(fieldRef)
          .append(");\n");
    } else if (paramSimple.endsWith("[]")) {
      String elemSimple = paramSimple.substring(0, paramSimple.length() - 2);
      String fqnParam = resolveTypeFQN(paramType, method, servicePackage);
      String elemFqn =
          fqnParam.endsWith("[]") ? fqnParam.substring(0, fqnParam.length() - 2) : fqnParam;
      sb.append("        ")
          .append(fqnParam)
          .append(" ")
          .append(argName)
          .append(" = ")
          .append(fieldRef)
          .append(" == null ? null : java.util.Arrays.stream(")
          .append(fieldRef)
          .append(")")
          .append(".map(")
          .append(basePackage)
          .append(".mapper.")
          .append(elemSimple)
          .append("Mapper::fromDto)")
          .append(".toArray(")
          .append(elemFqn)
          .append("[]::new);\n");
    } else {
      String fqnParam = resolveTypeFQN(paramType, method, servicePackage);
      sb.append("        ")
          .append(fqnParam)
          .append(" ")
          .append(argName)
          .append(" = ")
          .append(basePackage)
          .append(".mapper.")
          .append(paramSimple)
          .append("Mapper.fromDto(")
          .append(fieldRef)
          .append(");\n");
    }
  }

  /** コントローラーメソッドの戻り型文字列を解決します out引数ありなら ResponseDto、なければ CORBA 型または DTO 型を返します */
  private static String resolveControllerReturnType(
      MethodDeclaration method,
      String basePackage,
      String dtoPackage,
      String servicePackage,
      boolean returnAsDto,
      boolean hasOutParams) {
    if (hasOutParams)
      return dtoPackage + "." + capitalize(method.getNameAsString()) + "ResponseDto";
    String returnType = method.getType().asString();
    if ("void".equals(returnType)) return "Object";
    if (isPrimitiveType(returnType)) return returnType;
    if ("org.omg.CORBA.Any".equals(returnType)) {
      return returnAsDto ? basePackage + ".dto.AnyValue" : "org.omg.CORBA.Any";
    }
    if (!returnAsDto) return resolveTypeFQN(returnType, method, servicePackage);
    boolean isArray = returnType.endsWith("[]");
    String elem = isArray ? returnType.substring(0, returnType.length() - 2) : returnType;
    return dtoPackage + "." + simpleName(elem) + "Dto" + (isArray ? "[]" : "");
  }

  /**
   * CORBA 値を DTO へ変換する式（代入 RHS として使える文字列）を返します
   *
   * <p>{@code returnAsDto=false} またはプリミティブ/String/Any の場合は {@code rhs} をそのまま返します 配列型は {@code
   * Arrays.stream().map(Mapper::toDto).toArray()} 形式、 独自型は {@code XxxMapper.toDto(rhs)} 形式になります
   *
   * @param rhs 変換元の式（例: "result", "arg0.value"）
   * @param valueType CORBA 側の型名（配列型 "Foo[]" でも可）
   * @param basePackage ベースパッケージ名
   * @param dtoPackage DTO パッケージ名
   * @param returnAsDto false の場合は変換を行わず rhs をそのまま返す
   * @return 代入 RHS として使える式文字列
   */
  private static String corbaToDtoExpr(
      String rhs, String valueType, String basePackage, String dtoPackage, boolean returnAsDto) {
    if (!returnAsDto || isPrimitiveType(valueType) || "org.omg.CORBA.Any".equals(valueType)) {
      return rhs;
    }
    boolean isArray = valueType.endsWith("[]");
    String elem = isArray ? valueType.substring(0, valueType.length() - 2) : valueType;
    String elemSimple = simpleName(elem);
    if (isArray) {
      return rhs
          + " == null ? null : java.util.Arrays.stream("
          + rhs
          + ")"
          + ".map("
          + basePackage
          + ".mapper."
          + elemSimple
          + "Mapper::toDto)"
          + ".toArray("
          + dtoPackage
          + "."
          + elemSimple
          + "Dto[]::new)";
    }
    return basePackage + ".mapper." + elemSimple + "Mapper.toDto(" + rhs + ")";
  }

  /**
   * CORBA 型名をジェネレーター内部の DTO 型名へマップします
   *
   * <ul>
   *   <li>配列型: 要素型を再帰変換して {@code []} を付け直す（例: {@code com.pkg.Foo[] → dto.FooDto[]}）
   *   <li>{@code org.omg.CORBA.Any}: {@code AnyValue} へマップ
   *   <li>FQN を持つ型: 単純名 + {@code "Dto"}
   *   <li>プリミティブ/String: そのまま返す
   *   <li>単純名の独自型: パッケージプレフィックスを追加して {@code "Dto"} を付ける
   * </ul>
   *
   * @param basePackage ベースパッケージ名
   * @param typeNameString マップ元の型名
   * @return DTO 型名
   */
  private static String mapToDtoType(String basePackage, String typeNameString) {
    if (typeNameString.endsWith("[]")) {
      return mapToDtoType(basePackage, typeNameString.substring(0, typeNameString.length() - 2))
          + "[]";
    }
    if ("org.omg.CORBA.Any".equals(typeNameString)) return basePackage + ".dto.AnyValue";
    if (typeNameString.contains("."))
      return basePackage + ".dto." + simpleName(typeNameString) + "Dto";
    if (isPrimitiveType(typeNameString)) return typeNameString;
    return basePackage + ".dto." + typeNameString + "Dto";
  }

  /**
   * 型名文字列を JavaPoet {@link TypeName} へ変換します
   *
   * <p>JavaPoet の {@code ClassName.bestGuess} は小文字始まりの単純名（例: {@code pptFoo_struct}）で
   * 例外を投げるため、{@code lastIndexOf('.')} でパッケージと単純名を分割して {@link ClassName#get(String, String)} を使います
   *
   * @param type 変換元の型名
   * @param dtoPkgName DTO パッケージ名（同パッケージのクラス解決に使用）
   * @return 対応する {@link TypeName}
   */
  private static TypeName convToTypeName(String type, String dtoPkgName) {
    if (type.endsWith("[]")) {
      return ArrayTypeName.of(convToTypeName(type.substring(0, type.length() - 2), dtoPkgName));
    }
    switch (type) {
      case "int":
        return TypeName.INT;
      case "long":
        return TypeName.LONG;
      case "short":
        return TypeName.SHORT;
      case "byte":
        return TypeName.BYTE;
      case "boolean":
        return TypeName.BOOLEAN;
      case "float":
        return TypeName.FLOAT;
      case "double":
        return TypeName.DOUBLE;
      case "char":
        return TypeName.CHAR;
      case "String":
        return ClassName.get(String.class);
      case "Object":
        return ClassName.get(Object.class);
    }
    if (type.startsWith(dtoPkgName + ".")) {
      return ClassName.get(dtoPkgName, type.substring(type.lastIndexOf('.') + 1));
    }
    if (type.contains(".")) {
      return ClassName.get(
          type.substring(0, type.lastIndexOf('.')), type.substring(type.lastIndexOf('.') + 1));
    }
    return ClassName.get("", type);
  }

  /**
   * メソッド定義の import 文を参照して単純型名を完全修飾名に解決します
   *
   * <p>JavaParser はシンボル解決なしでパースするため、IDLから生成されたスタブの import 文をスキャンして正しいパッケージを特定しますグローバルスコープの型が
   * idlPackagePrefix 直下へ再配置された場合でも正しく解決できます
   *
   * @param type 解決したい型名（単純名または配列型、例: "GlobalTag[]"）
   * @param method 型が参照されているメソッド宣言（import 文のスキャンに使用）
   * @param fallbackPackage import で見つからない場合のフォールバックパッケージ
   * @return 完全修飾型名（すでに FQN または primitive の場合はそのまま返す）
   */
  private static String resolveTypeFQN(
      String type, MethodDeclaration method, String fallbackPackage) {
    if (type.contains(".") || isPrimitiveType(type) || "void".equals(type)) return type;
    boolean isArray = type.endsWith("[]");
    String elem = isArray ? type.substring(0, type.length() - 2) : type;
    String fqn =
        method
            .findCompilationUnit()
            .map(
                cu ->
                    cu.getImports().stream()
                        .filter(imp -> !imp.isAsterisk())
                        .filter(imp -> imp.getNameAsString().endsWith("." + elem))
                        .findFirst()
                        .map(imp -> imp.getNameAsString())
                        .orElse(null))
            .orElse(null);
    if (fqn == null && !fallbackPackage.isEmpty()) fqn = fallbackPackage + "." + elem;
    if (fqn == null) return type;
    return isArray ? fqn + "[]" : fqn;
  }

  /**
   * Holder 型（CORBA out/inout パラメーター）の {@code value} フィールドの型名を返します
   *
   * <p>CORBA プリミティブ Holder（{@code org.omg.CORBA.IntHolder} 等）はハードコードで対応し、 独自 Holder はスタブファイルを解析して
   * {@code value} フィールドの型を取得します idlj は sequence typedef に対して配列型の {@code value} フィールドを生成するため （例:
   * {@code GlobalTag value[]}）、ファイル解析が必要です
   *
   * @param holderType Holder 型の名前（単純名または FQN）
   * @param method 型が参照されているメソッド宣言
   * @param fallbackPackage FQN 解決のフォールバックパッケージ
   * @param stubRoot スタブのルートディレクトリ（Holder ファイル解析に使用）
   * @return value フィールドの型名
   */
  private static String resolveHolderValueType(
      String holderType, MethodDeclaration method, String fallbackPackage, Path stubRoot) {
    String fqn =
        holderType.contains(".") ? holderType : resolveTypeFQN(holderType, method, fallbackPackage);
    switch (fqn) {
      case "org.omg.CORBA.IntHolder":
        return "int";
      case "org.omg.CORBA.LongHolder":
        return "long";
      case "org.omg.CORBA.ShortHolder":
        return "short";
      case "org.omg.CORBA.BooleanHolder":
        return "boolean";
      case "org.omg.CORBA.FloatHolder":
        return "float";
      case "org.omg.CORBA.DoubleHolder":
        return "double";
      case "org.omg.CORBA.StringHolder":
        return "String";
      case "org.omg.CORBA.ByteHolder":
        return "byte";
      case "org.omg.CORBA.CharHolder":
        return "char";
      case "org.omg.CORBA.AnyHolder":
        return "org.omg.CORBA.Any";
    }
    if (fqn.contains(".") && stubRoot != null) {
      String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
      String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
      Path holderFile = stubRoot.resolve(pkg.replace('.', '/') + "/" + simpleName + ".java");
      if (Files.exists(holderFile)) {
        try {
          return StaticJavaParser.parse(holderFile)
              .findAll(ClassOrInterfaceDeclaration.class)
              .stream()
              .flatMap(c -> c.getFields().stream())
              .flatMap(f -> f.getVariables().stream())
              .filter(v -> v.getNameAsString().equals("value"))
              .findFirst()
              .map(v -> v.getType().asString())
              .orElseGet(() -> stripHolder(fqn));
        } catch (IOException e) {
          // フォールバックへ
        }
      }
    }
    return stripHolder(fqn);
  }

  /** "FooHolder" → "Foo" のように Holder サフィックスを除去する */
  private static String stripHolder(String fqn) {
    return fqn.endsWith("Holder") ? fqn.substring(0, fqn.length() - "Holder".length()) : fqn;
  }

  /**
   * プリミティブ型または String かどうかを判定します（配列は要素型で再帰判定） String を含むのは、IDL の string 型が Java の String にマップされ、
   * DTO 変換不要のプリミティブ相当として扱うためです
   */
  private static boolean isPrimitiveType(String type) {
    if (type == null) return false;
    if (type.endsWith("[]")) return isPrimitiveType(type.substring(0, type.length() - 2));
    switch (type) {
      case "int":
      case "long":
      case "short":
      case "byte":
      case "boolean":
      case "float":
      case "double":
      case "char":
      case "String":
        return true;
      default:
        return false;
    }
  }

  /** Holder 型（CORBA out/inout パラメーター）かどうかを判定します 単純名が {@code "Holder"} で終わるクラスを Holder 型とみなします */
  private static boolean isHolderType(String typeName) {
    return simpleName(typeName).endsWith("Holder");
  }

  /** "Operations" サフィックスを取り除いたサービス名を返す */
  private static String toServiceName(ClassOrInterfaceDeclaration serviceIface) {
    String name = serviceIface.getNameAsString();
    return name.endsWith("Operations")
        ? name.substring(0, name.length() - "Operations".length())
        : name;
  }

  /** 完全修飾型名から単純名を取り出す（例: "com.pkg.Foo" → "Foo"、"Foo" → "Foo"） */
  private static String simpleName(String type) {
    return type.contains(".") ? type.substring(type.lastIndexOf('.') + 1) : type;
  }

  /** 文字列の先頭を大文字にする */
  private static String capitalize(String s) {
    return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
