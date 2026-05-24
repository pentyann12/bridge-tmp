package com.example.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;

/**
 * CORBA ⇔ DTO のMapper自動生成クラス
 */
public class MapperGenerator {

    /**
     * 指定クラスからMapperクラスを生成する。
     * @param clazz 対象クラス
     * @param outputDir 生成ファイルの出力先ディレクトリ
     * @param basePackage 生成クラスのベースパッケージ
     * @throws IOException ファイル出力エラー
     */
    public static void generate(
            ClassOrInterfaceDeclaration clazz,
            Path outputDir,
            String basePackage) throws IOException {
        String name = clazz.getNameAsString();
        String dtoName = name + "Dto";
        String mapperName = name + "Mapper";
        String dtoPackage = basePackage + ".dto";
        ClassName dtoClass = ClassName.get(dtoPackage, dtoName);
        String corbaPackage = clazz.findCompilationUnit()
                .flatMap(cu -> cu.getPackageDeclaration().map(pd -> pd.getNameAsString()))
                .orElse("");
        ClassName corbaClass = corbaPackage.isEmpty() ? ClassName.bestGuess(name) : ClassName.get(corbaPackage, name);

        TypeSpec.Builder mapper = TypeSpec.classBuilder(mapperName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // ===== toDTO =====
        MethodSpec.Builder toDtoBody = MethodSpec.methodBuilder("toDto")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(dtoClass)
                .addParameter(corbaClass, "src")
                .addStatement("$T dto = new $T()", dtoClass, dtoClass);

        // ===== fromDTO =====
        MethodSpec.Builder fromDtoBody = MethodSpec.methodBuilder("fromDto")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(corbaClass)
                .addParameter(dtoClass, "src")
                .addStatement("$T dst = new $T()", corbaClass, corbaClass);

        ClassName anyValueClass = ClassName.get(dtoPackage, "AnyValue");
        ClassName anyClass = ClassName.get("org.omg.CORBA", "Any");
        ClassName orbClass = ClassName.get("org.omg.CORBA", "ORB");
        ClassName tcKindClass = ClassName.get("org.omg.CORBA", "TCKind");
        ClassName corbaObjectClass = ClassName.get("org.omg.CORBA", "Object");

        boolean hasAny = false;

        for (FieldDeclaration field : clazz.getFields()) {

            String type = field.getElementType().asString();
            String simple = type.contains(".") ? type.substring(type.lastIndexOf('.') + 1) : type;
            boolean isAny = type.equals("org.omg.CORBA.Any");
            boolean isPrimitive = isPrimitiveType(simple);

            for (var v : field.getVariables()) {
                String nameField = v.getNameAsString();

                if (isAny) {
                    hasAny = true;
                    toDtoBody.addStatement("dto.$N = toAnyValue($N.$N)", nameField, "src", nameField);
                    fromDtoBody.addStatement("dst.$N = toAny(src.$N)", nameField, nameField);
                } else if (isPrimitive) {
                    toDtoBody.addStatement("dto.$N = $N.$N", nameField, "src", nameField);
                    fromDtoBody.addStatement("dst.$N = src.$N", nameField, nameField);
                } else {
                    ClassName nestedMapperClass = ClassName.get(basePackage + ".mapper", simple + "Mapper");
                    toDtoBody.addStatement("dto.$N = $T.toDto($N.$N)", nameField, nestedMapperClass, "src", nameField);
                    fromDtoBody.addStatement("dst.$N = $T.fromDto(src.$N)", nameField, nestedMapperClass, nameField);
                }
            }
        }

        toDtoBody.addStatement("return dto");
        fromDtoBody.addStatement("return dst");

        mapper.addMethod(toDtoBody.build());
        mapper.addMethod(fromDtoBody.build());

        if (hasAny) {
            mapper.addMethod(MethodSpec.methodBuilder("toAnyValue")
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .returns(anyValueClass)
                    .addParameter(anyClass, "any")
                    .beginControlFlow("if (any == null)")
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement("$T kind = any.type().kind()", tcKindClass)
                    .addStatement("if (kind.equals($T.tk_boolean)) return new $T(\"boolean\", any.extract_boolean())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_short)) return new $T(\"short\", any.extract_short())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_ushort)) return new $T(\"short\", any.extract_ushort())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_long)) return new $T(\"int\", any.extract_long())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_ulong)) return new $T(\"int\", any.extract_ulong())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_longlong)) return new $T(\"long\", any.extract_longlong())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_ulonglong)) return new $T(\"long\", any.extract_ulonglong())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_float)) return new $T(\"float\", any.extract_float())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_double)) return new $T(\"double\", any.extract_double())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_char)) return new $T(\"char\", any.extract_char())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_wchar)) return new $T(\"char\", any.extract_wchar())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_octet)) return new $T(\"byte\", any.extract_octet())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_string)) return new $T(\"string\", any.extract_string())", tcKindClass, anyValueClass)
                    .addStatement("else if (kind.equals($T.tk_any)) return new $T(\"any\", toAnyValue(any.extract_any()))", tcKindClass, anyValueClass)
                    .addStatement("else return new $T(\"object\", any.extract_Object())", anyValueClass)
                    .build());

            mapper.addMethod(MethodSpec.methodBuilder("toAny")
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .returns(anyClass)
                    .addParameter(anyValueClass, "dto")
                    .beginControlFlow("if (dto == null)")
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement("$T any = $T.init().create_any()", anyClass, orbClass)
                    .beginControlFlow("if (dto.type == null)")
                    .addStatement("return any")
                    .endControlFlow()
                    .beginControlFlow("switch (dto.type)")
                    .addStatement("case \"boolean\": any.insert_boolean(($T) dto.value); break", Boolean.class)
                    .addStatement("case \"short\": any.insert_short(((Number) dto.value).shortValue()); break")
                    .addStatement("case \"int\": any.insert_long(((Number) dto.value).intValue()); break")
                    .addStatement("case \"long\": any.insert_longlong(((Number) dto.value).longValue()); break")
                    .addStatement("case \"float\": any.insert_float(((Number) dto.value).floatValue()); break")
                    .addStatement("case \"double\": any.insert_double(((Number) dto.value).doubleValue()); break")
                    .addStatement("case \"char\": any.insert_char((Character) dto.value); break")
                    .addStatement("case \"byte\": any.insert_octet((Byte) dto.value); break")
                    .addStatement("case \"string\": any.insert_string(String.valueOf(dto.value)); break")
                    .addStatement("case \"any\": any.insert_any(toAny(($T) dto.value)); break", anyValueClass)
                    .addStatement("case \"object\": any.insert_Object(($T) dto.value); break", corbaObjectClass)
                    .addStatement("default: any.insert_any($T.init().create_any()); break", orbClass)
                    .endControlFlow()
                    .addStatement("return any")
                    .build());
        }

        JavaFile.builder(basePackage + ".mapper", mapper.build())
                .build()
                .writeTo(outputDir);
    }

    /**
     * プリミティブ型かどうか判定する。
     * @param type 型名
     * @return プリミティブ型ならtrue
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
            case "String": // CORBA IDLのstringはJavaのStringにマッピングされるため、プリミティブ扱いする
                return true;
            default:
                return false;
        }
    }
}