package com.example.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** IDL スタブのコピーと構造体クラス収集を担うユーティリティ */
final class StubHandler {
  private StubHandler() {}

  /**
   * スタブを生成先プロジェクトへコピーする
   *
   * @param stubSourceRoot スタブディレクトリ
   * @param javaDir コピー先ディレクトリ
   */
  static void copyIdlStubSources(Path stubSourceRoot, Path javaDir) throws IOException {
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
   * スタブから CORBA 構造体クラスの一覧を収集 インターフェース・Helper・Holder・POA・Stub・メソッドを持つクラスは除外
   *
   * @param stubSourceRoot スタブのルートディレクトリ
   * @return 構造体クラスの一覧
   * @throws IOException スタブの走査に失敗した場合
   */
  static List<ClassOrInterfaceDeclaration> collectStructClasses(Path stubSourceRoot)
      throws IOException {
    List<ClassOrInterfaceDeclaration> structs = new ArrayList<>();
    Files.walk(stubSourceRoot)
        .filter(Files::isRegularFile)
        .filter(p -> p.toString().endsWith(".java"))
        .forEach(
            p -> {
              try {
                StaticJavaParser.parse(p).findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(StubHandler::isTargetStruct)
                    .forEach(structs::add);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    return structs;
  }

  /** DTO/Mapper 生成対象の構造体クラスかどうかを判定 インターフェース・各種 idlj 生成クラス（Helper/Holder/POA 等）・メソッドを持つクラスは除外 */
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
}
