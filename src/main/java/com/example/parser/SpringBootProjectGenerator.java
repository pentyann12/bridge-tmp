package com.example.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CORBA サービスを REST エンドポイントとして公開する単体の Spring Boot
 * プロジェクト（Gradle）を生成するユーティリティ。
 */
public class SpringBootProjectGenerator {

  /**
   * 指定された CORBA サービスインターフェースから Spring Boot プロジェクトを生成します。
   * 生成されるプロジェクトは、サービスの各メソッドを POST エンドポイントとして公開し、CORBA クライアントを通じてサービス呼び出しを行います。
   *
   * @param serviceIface   CORBA サービスインターフェースのクラス宣言
   * @param stubSourceRoot idlj によって生成されたスタブソースのルートディレクトリ
   * @param outputDir      生成する Spring Boot プロジェクトの出力ディレクトリ
   * @param basePkgName    生成プロジェクトのベースパッケージ名
   * @param returnAsDto    CORBA サービスの戻り値を DTO として返すかどうか（true の場合、複雑な型は DTO
   *                       に変換される）
   * @throws IOException ファイル操作に失敗した場合
   */
  public static void generate(ClassOrInterfaceDeclaration serviceIface,
      Path stubSourceRoot,
      Path outputDir,
      String basePkgName,
      boolean returnAsDto) throws IOException {

    String serviceName = serviceIface.getNameAsString();
    if (serviceName.endsWith("Operations")) {
      serviceName = serviceName.substring(0, serviceName.length() - "Operations".length());
    }

    String servicePackage = serviceIface.findCompilationUnit()
        .flatMap(CompilationUnit::getPackageDeclaration)
        .map(PackageDeclaration::getNameAsString)
        .orElse("");

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

    List<ClassOrInterfaceDeclaration> structClasses = collectStructClasses(stubSourceRoot);
    for (ClassOrInterfaceDeclaration structClass : structClasses) {
      DtoGenerator.generate(structClass, javaDir, basePkgName);
      MapperGenerator.generate(structClass, javaDir, basePkgName);
    }

    List<MethodDeclaration> methods = serviceIface.getMethods();
    for (MethodDeclaration method : methods) {
      generateRequestDto(method, javaDir, basePkgName);
    }

    writeCorbaClient(serviceName, servicePackage, methods, corbaDir, basePkgName);
    writeController(serviceName, servicePackage, methods, controllerDir, basePkgName, returnAsDto);
    writeApplicationProperties(outputDir, serviceName);
  }

  /**
   * build.gradleを出力する
   *
   * @param outputDir 出力先ディレクトリ
   */
  private static void writeBuildGradle(Path outputDir) throws IOException {
    String buildGradle = """
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
    Files.writeString(outputDir.resolve("build.gradle"), buildGradle,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * エントリポイントクラスを出力する
   *
   * @param basePkgName ベースパッケージ名
   * @param pkgBaseDir  出力先のソースルートディレクトリ
   */
  private static void writeApplicationClass(String basePkgName, Path pkgBaseDir) throws IOException {
    String appClass = """
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
        """.formatted(basePkgName);
    Files.writeString(pkgBaseDir.resolve("Application.java"), appClass,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * サーブレットクラスを出力する
   *
   * @param basePkgName ベースパッケージ名
   * @param pkgBaseDir  出力先のソースルートディレクトリ
   */
  private static void writeServletClass(String basePkgName, Path pkgBaseDir) throws IOException {
    String servletClass = """
        package %s;

        import org.springframework.boot.builder.SpringApplicationBuilder;
        import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

        public class ServletInitializer extends SpringBootServletInitializer {
          @Override
          protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
            return application.sources(Application.class);
          }
        }
        """.formatted(basePkgName);
    Files.writeString(pkgBaseDir.resolve("ServletInitializer.java"), servletClass,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * idlスタブを生成先プロジェクトへコピーする
   *
   * @param stubSourceRoot idlスタブルートディレクトリ
   * @param javaDir        コピー先のJavaソースルートディレクトリ
   */
  private static void copyIdlStubSources(Path stubSourceRoot, Path javaDir) throws IOException {
    // 各javaファイルを相対PATHを維持して生成先へコピーする
    Files.walk(stubSourceRoot)
        .filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(".java"))
        .forEach(path -> {
          try {
            Path target = javaDir.resolve(stubSourceRoot.relativize(path));
            Files.createDirectories(target.getParent());
            Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  /**
   * AnyValueクラスを出力する
   *
   * CORBA.Anyを代替するDTOクラスとしてのAnyValueクラスを定義
   *
   * @param dtoDir      DTOソースディレクトリ
   * @param basePkgName ベースパッケージ名
   */
  private static void writeAnyValueDto(Path dtoDir, String basePkgName) throws IOException {
    String anyValue = """
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
        """.formatted(basePkgName);
    Files.writeString(dtoDir.resolve("AnyValue.java"), anyValue,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * CORBA.AnyとAnyValueを相互変換するマッパークラスを出力する
   *
   * @param mapperDir   mapperソースディレクトリ
   * @param basePkgName ベースパッケージ名
   */
  private static void writeAnyMapper(Path mapperDir, String basePkgName) throws IOException {
    Files.createDirectories(mapperDir);
    String pkg = basePkgName + ".mapper";
    String anyMapper = """
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

        """.formatted(pkg, basePkgName);
    Files.writeString(mapperDir.resolve("AnyMapper.java"), anyMapper,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  // MARK: ここまで雛形

  /**
   * idlスタブからデータ構造クラスの一覧を取得する
   *
   * @param stubSourceRoot
   * @return
   */
  private static List<ClassOrInterfaceDeclaration> collectStructClasses(Path stubSourceRoot) throws IOException {
    List<ClassOrInterfaceDeclaration> structs = new ArrayList<>();
    Files.walk(stubSourceRoot)
        .filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(".java"))
        .forEach(path -> {
          try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(SpringBootProjectGenerator::isTargetStruct)
                // TODO: 関数を分離せずこっちでもいいと思う
                // .filter(clazz -> !clazz.isInterface())
                // .filter(clazz -> {
                // String name = clazz.getNameAsString();
                // return !name.endsWith("Helper")
                // && !name.endsWith("Holder")
                // && !name.endsWith("Operations")
                // && !name.endsWith("POA")
                // && !name.endsWith("Stub")
                // && !name.startsWith("_");
                // })
                // .filter(clazz -> clazz.getMethods().isEmpty())
                .forEach(structs::add);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
    return structs;
  }

  private static boolean isTargetStruct(ClassOrInterfaceDeclaration clazz) {
    if (clazz.isInterface()) {
      return false;
    }

    String name = clazz.getNameAsString();
    if (name.endsWith("Helper")
        || name.endsWith("Holder")
        || name.endsWith("Operations")
        || name.endsWith("POA")
        || name.endsWith("Stub")
        || name.startsWith("_")) {
      return false;
    }

    return !clazz.getMethods().stream().findAny().isPresent();
  }

  /**
   * CORBAサービスメソッドに対応して、パラメータをフィールドに持つリクエストDTOを出力する
   *
   * @param method      サービスメソッドの宣言
   * @param outputRoot  生成プロジェクトのソース出力ルートディレクトリ
   * @param basePkgName ベースパッケージ名
   */
  private static void generateRequestDto(MethodDeclaration method, Path outputRoot, String basePkgName)
      throws IOException {
    String dtoPkgName = basePkgName + ".dto";
    String reqDtoName = capitalize(method.getNameAsString()) + "RequestDto";
    TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(reqDtoName)
        .addModifiers(Modifier.PUBLIC);

    // パラメータをDTO型として解決してフィールドに追加する
    for (Parameter parameter : method.getParameters()) {
      String fieldType = mapToDtoType(basePkgName, parameter.getType().asString());
      TypeName typeName = convToTypeName(fieldType, dtoPkgName);
      FieldSpec field = FieldSpec.builder(typeName, parameter.getNameAsString(), Modifier.PUBLIC).build();
      typeBuilder.addField(field);
    }

    JavaFile javaFile = JavaFile.builder(dtoPkgName, typeBuilder.build()).build();
    javaFile.writeTo(outputRoot);
  }

  /**
   * 型名をDTO型名へマップする
   * 
   * @param basePackage
   * @param typeNameString
   * @return
   */
  private static String mapToDtoType(String basePackage, String typeNameString) {
    if (typeNameString.equals("org.omg.CORBA.Any")) {
      return basePackage + ".dto.AnyValue";
    }
    if (typeNameString.contains(".")) {
      String simple = typeNameString.substring(typeNameString.lastIndexOf('.') + 1);
      return basePackage + ".dto." + simple + "Dto";
    }
    return typeNameString;
  }

  /**
   * 型名（文字列）をTypeNameへ変換する
   * 
   * @param type       型名
   * @param dtoPkgName DTOパッケージ名
   * @return TypeName
   */
  private static TypeName convToTypeName(String type, String dtoPkgName) {
    if (type.equals("int"))
      return TypeName.INT;
    if (type.equals("long"))
      return TypeName.LONG;
    if (type.equals("short"))
      return TypeName.SHORT;
    if (type.equals("byte"))
      return TypeName.BYTE;
    if (type.equals("boolean"))
      return TypeName.BOOLEAN;
    if (type.equals("float"))
      return TypeName.FLOAT;
    if (type.equals("double"))
      return TypeName.DOUBLE;
    if (type.equals("char"))
      return TypeName.CHAR;
    if (type.equals("String"))
      return ClassName.get(String.class);
    if (type.equals("Object"))
      return ClassName.get(Object.class);
    if (type.equals("AnyValue"))
      return ClassName.get(dtoPkgName, "AnyValue");
    if (type.startsWith(dtoPkgName + ".")) {
      return ClassName.get(dtoPkgName, type.substring(type.lastIndexOf('.') + 1));
    }
    if (type.contains(".")) {
      return ClassName.bestGuess(type);
    }
    return ClassName.bestGuess(type);
  }

  /**
   * 
   * @param serviceName
   * @param servicePackage
   * @param methods
   * @param controllerDir
   * @param basePackage
   * @param returnAsDto
   * @throws IOException
   */
  private static void writeController(String serviceName,
      String servicePackage,
      List<MethodDeclaration> methods,
      Path controllerDir,
      String basePackage,
      boolean returnAsDto) throws IOException {
    String controllerName = serviceName + "Controller";
    StringBuilder methodsCode = new StringBuilder();
    String dtoPackage = basePackage + ".dto";

    for (MethodDeclaration method : methods) {
      String methodName = method.getNameAsString();
      String requestDtoType = dtoPackage + "." + capitalize(methodName) + "RequestDto";
      boolean hasRequestBody = !method.getParameters().isEmpty();
      String handlerName = "req";
      methodsCode.append("    @PostMapping(\"/" + methodName + "\")\n");
      String returnType = method.getType().asString();
      String simpleReturnType = returnType.contains(".") ? returnType.substring(returnType.lastIndexOf('.') + 1)
          : returnType;
      String controllerReturnType;
      if ("void".equals(returnType)) {
        controllerReturnType = "Object";
      } else if (isPrimitiveType(simpleReturnType) || "String".equals(simpleReturnType)) {
        controllerReturnType = returnType;
      } else if ("org.omg.CORBA.Any".equals(returnType)) {
        controllerReturnType = returnAsDto ? basePackage + ".dto.AnyValue" : "org.omg.CORBA.Any";
      } else {
        controllerReturnType = returnAsDto ? dtoPackage + "." + simpleReturnType + "Dto"
            : originalType(returnType, servicePackage);
      }

      methodsCode.append("    public " + controllerReturnType + " " + methodName + "(");
      if (hasRequestBody) {
        methodsCode.append("@RequestBody " + requestDtoType + " " + handlerName);
      }
      methodsCode.append(") {\n");
      methodsCode.append("        " + basePackage + ".corba.CorbaClient client = new " + basePackage
          + ".corba.CorbaClient(System.getProperty(\"corba." + serviceName + ".ior\", \"\"));\n");

      List<String> argNames = new ArrayList<>();
      for (int i = 0; i < method.getParameters().size(); i++) {
        Parameter parameter = method.getParameters().get(i);
        String originalType = parameter.getType().asString();
        String simple = originalType.contains(".") ? originalType.substring(originalType.lastIndexOf('.') + 1)
            : originalType;
        String argName = "arg" + i;
        if (isPrimitiveType(simple) || "String".equals(simple)) {
          methodsCode.append("        " + originalType + " " + argName + " = " + handlerName + "."
              + parameter.getNameAsString() + ";\n");
        } else if ("org.omg.CORBA.Any".equals(originalType)) {
          methodsCode.append("        org.omg.CORBA.Any " + argName + " = " + basePackage
              + ".mapper.AnyMapper.toAny(" + handlerName + "." + parameter.getNameAsString() + ");\n");
        } else {
          methodsCode.append("        " + originalType + " " + argName + " = " + basePackage + ".mapper."
              + simple + "Mapper.fromDto(" + handlerName + "." + parameter.getNameAsString() + ");\n");
        }
        argNames.add(argName);
      }

      String callArgs = String.join(", ", argNames);

      if ("void".equals(returnType)) {
        methodsCode.append("        client." + methodName + "(" + callArgs + ");\n");
        methodsCode.append("        return java.util.Collections.singletonMap(\"status\", \"ok\");\n");
      } else if (isPrimitiveType(simpleReturnType) || "String".equals(simpleReturnType)) {
        methodsCode.append("        return client." + methodName + "(" + callArgs + ");\n");
      } else if ("org.omg.CORBA.Any".equals(returnType)) {
        if (returnAsDto) {
          methodsCode.append("        return " + basePackage + ".mapper.AnyMapper.toAnyValue(client."
              + methodName + "(" + callArgs + "));\n");
        } else {
          methodsCode.append("        return client." + methodName + "(" + callArgs + ");\n");
        }
      } else {
        methodsCode.append("        " + originalType(returnType, servicePackage) + " result = client."
            + methodName + "(" + callArgs + ");\n");
        if (returnAsDto) {
          methodsCode.append("        return " + basePackage + ".mapper." + simpleReturnType
              + "Mapper.toDto(result);\n");
        } else {
          methodsCode.append("        return result;\n");
        }
      }

      methodsCode.append("    }\n\n");
    }

    String controllerFull = """
        package %s.controller;

        import org.springframework.web.bind.annotation.RestController;
        import org.springframework.web.bind.annotation.RequestMapping;
        import org.springframework.web.bind.annotation.PostMapping;
        import org.springframework.web.bind.annotation.RequestBody;

        @RestController
        @RequestMapping("/")
        public class %s {
        %s
        }
        """.formatted(basePackage, controllerName, methodsCode.toString());

    Files.writeString(controllerDir.resolve(controllerName + ".java"), controllerFull,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * 文字列の先頭を大文字にする
   * 
   * @param s
   * @return
   */
  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * 
   * @param returnType
   * @param servicePackage
   * @return
   */
  private static String originalType(String returnType, String servicePackage) {
    if (returnType.contains(".")) {
      return returnType;
    }
    return servicePackage.isEmpty() ? returnType : servicePackage + "." + returnType;
  }

  /**
   * CORBA サービス呼び出しをラップする `CorbaClient` クラスを生成します。
   * サービスの IOR を渡すことで実際の CORBA 実装へブリッジします。
   *
   * @param serviceName    サービス名
   * @param servicePackage サービスが属するパッケージ名
   * @param methods        サービスインターフェースのメソッド一覧
   * @param corbaDir       生成する `CorbaClient` の出力ディレクトリ
   * @param basePackage    生成プロジェクトのベースパッケージ名
   */
  private static void writeCorbaClient(
      String serviceName,
      String servicePackage,
      List<MethodDeclaration> methods,
      Path corbaDir,
      String basePackage
    ) throws IOException {

    StringBuilder builder = new StringBuilder();
    builder.append("package " + basePackage + ".corba;\n\n");
    builder.append("import org.omg.CORBA.ORB;\n");
    builder.append("import org.omg.CORBA.Object;\n");
    if (!servicePackage.isEmpty()) {
      builder.append("import " + servicePackage + "." + serviceName + ";\n");
      builder.append("import " + servicePackage + "." + serviceName + "Helper;\n");
    }
    builder.append("\n");
    builder.append("public class CorbaClient {\n");
    builder.append("    private final ORB orb;\n");
    builder.append("    private final " + serviceName + " service;\n\n");
    builder.append("    public CorbaClient(String ior) {\n");
    builder.append("        this.orb = ORB.init(new String[0], null);\n");
    builder.append("        org.omg.CORBA.Object obj = orb.string_to_object(ior);\n");
    builder.append("        this.service = " + serviceName + "Helper.narrow(obj);\n");
    builder.append("    }\n\n");

    for (MethodDeclaration method : methods) {
      String returnType = method.getType().asString();
      String declaredReturnType = returnType;
      if (!returnType.contains(".")
          && !isPrimitiveType(returnType)
          && !"void".equals(returnType)
          && !"String".equals(returnType)
          && !servicePackage.isEmpty()) {
        declaredReturnType = servicePackage + "." + returnType;
      }
      builder.append("    public " + declaredReturnType + " " + method.getNameAsString() + "(");
      for (int i = 0; i < method.getParameters().size(); i++) {
        Parameter parameter = method.getParameters().get(i);
        if (i > 0)
          builder.append(", ");
        builder.append(parameter.getType().asString()).append(" ").append(parameter.getNameAsString());
      }
      builder.append(") {\n");
      if ("void".equals(returnType)) {
        builder.append("        service." + method.getNameAsString() + "(");
        builder.append(method.getParameters().stream().map(Parameter::getNameAsString)
            .collect(Collectors.joining(", ")));
        builder.append(");\n");
      } else {
        builder.append("        return service." + method.getNameAsString() + "(");
        builder.append(method.getParameters().stream().map(Parameter::getNameAsString)
            .collect(Collectors.joining(", ")));
        builder.append(");\n");
      }
      builder.append("    }\n\n");
    }

    builder.append("}\n");

    Files.writeString(corbaDir.resolve("CorbaClient.java"), builder.toString(),
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * 生成プロジェクトの `application.properties` を書き込みます。
   * サービス IOR の設定箇所と OpenAPI 設定を含みます。
   *
   * @param outputDir   出力先ディレクトリ
   * @param serviceName サービス名
   * @throws IOException ファイル書き込みに失敗した場合
   */
  private static void writeApplicationProperties(Path outputDir, String serviceName) throws IOException {
    String props = """
        corba:
          %s:
            ior:
        """.formatted(serviceName);
    Files.writeString(outputDir.resolve("src/main/resources/application.yml"), props,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * 
   * @param type
   * @return
   */
  private static boolean isPrimitiveType(String type) {
    if (type == null)
      return false;
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

}
