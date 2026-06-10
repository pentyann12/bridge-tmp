package com.example.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

/** CORBA Any ↔ AnyValue 相互変換に使う雛形クラスを出力するユーティリティ */
final class AnyTypeWriter {
  private AnyTypeWriter() {}

  /**
   * AnyValue クラスを出力する
   *
   * @param dtoDir 出力先ディレクトリ
   * @param basePkgName ベースパッケージ名
   */
  static void writeAnyValueDto(Path dtoDir, String basePkgName) throws IOException {
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
   * CORBA.Any ⇔ AnyValue 相互変換 Mapper を出力する
   *
   * <p>{@code TCKind.tk_struct} については既知の構造体型ごとに TypeCode ID でスイッチして {@code XxxHelper.extract()} +
   * {@code XxxMapper.toDto()} を呼び出すコードを生成します。
   *
   * @param mapperDir Mapper ディレクトリ
   * @param basePkgName ベースパッケージ名
   * @param structClasses IDL スタブから収集した構造体クラス一覧
   */
  static void writeAnyMapper(
      Path mapperDir, String basePkgName, List<ClassOrInterfaceDeclaration> structClasses)
      throws IOException {
    String pkg = basePkgName + ".mapper";

    // TypeCode ID → Helper.extract() + Mapper.toDto() のケースを生成
    String structCases =
        structClasses.stream()
            .map(
                clazz -> {
                  String className = clazz.getNameAsString();
                  String classPkg =
                      clazz
                          .findCompilationUnit()
                          .flatMap(
                              cu ->
                                  cu.getPackageDeclaration()
                                      .map(pd -> pd.getNameAsString()))
                          .orElse("");
                  // CORBA リポジトリID: "IDL:pkg/ClassName:1.0"
                  String repoId =
                      "IDL:"
                          + (classPkg.isEmpty() ? "" : classPkg.replace('.', '/') + "/")
                          + className
                          + ":1.0";
                  String helperFqn =
                      classPkg.isEmpty()
                          ? className + "Helper"
                          : classPkg + "." + className + "Helper";
                  String mapperFqn = basePkgName + ".mapper." + className + "Mapper";
                  return "        case \"%s\": return new AnyValue(\"struct\", %s.toDto(%s.extract(any)));\n"
                      .formatted(repoId, mapperFqn, helperFqn);
                })
            .collect(Collectors.joining());

    // tk_struct ブランチ全体（構造体がなければ空文字列）
    String structBlock =
        structClasses.isEmpty()
            ? ""
            : "    else if (kind.equals(TCKind.tk_struct)) {\n"
                + "      String id = any.type().id();\n"
                + "      switch (id) {\n"
                + structCases
                + "        default: return new AnyValue(\"struct\", null);\n"
                + "      }\n"
                + "    }\n";

    // テンプレート内の //@@STRUCT@@ マーカーを structBlock で置換する
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
            //@@STRUCT@@
            else return new AnyValue("object", any.extract_Object());
          }
        }
        """
            .formatted(pkg, basePkgName)
            .replace("    //@@STRUCT@@\n", structBlock);

    Files.writeString(
        mapperDir.resolve("AnyMapper.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
