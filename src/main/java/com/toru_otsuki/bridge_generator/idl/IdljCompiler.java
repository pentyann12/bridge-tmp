package com.toru_otsuki.bridge_generator.idl;

import com.sun.tools.corba.ee.idl.EnumEntry;
import com.sun.tools.corba.ee.idl.ForwardEntry;
import com.sun.tools.corba.ee.idl.InterfaceEntry;
import com.sun.tools.corba.ee.idl.ModuleEntry;
import com.sun.tools.corba.ee.idl.StructEntry;
import com.sun.tools.corba.ee.idl.SymtabEntry;
import com.sun.tools.corba.ee.idl.TypedefEntry;
import com.sun.tools.corba.ee.idl.UnionEntry;
import com.sun.tools.corba.ee.idl.toJavaPortable.Arguments;
import com.sun.tools.corba.ee.idl.toJavaPortable.Compile;
import com.toru_otsuki.bridge_generator.GenerationException;
import com.toru_otsuki.bridge_generator.model.IdlModel;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// idljを同じプロセスで動かし、Javaスタブの生成と、生成に使った記号表からのモデル作成を1回で行う
///
/// idljのプリプロセッサ・構文解析・IDL→Javaの名前の対応規則をそのまま使うので、IDLの意味を推測する必要がなく、
/// スタブとモデルで名前が食い違うこともない。idljの内部API（公開APIではない）に触るのは、このクラスと[ModelBuilder]だけにする。
///
/// idljの拡張方法について:
///
/// - `Compile`を継承して拡張すること自体は、idljが想定している使い方（[基底クラスのJavadoc][idlj-compile]の「To Extend the
///   compiler」）。
/// - ただしそこで説明されているのは生成器（`GenFactory`）の差し替え。[#preEmit]を上書きしてパッケージ割り当ての前に介入するのは、
///   ソースを読んで決めたこのクラス独自の使い方。
/// - idljのバージョンを上げるときは、`start`が`preParse`・`parse`・`preEmit`・`generate`の順に呼ぶことと、
///   `preEmit`でパッケージを割り当てていることを確かめる。崩れた場合は`IdljCompilerTest`とゴールデンテストが失敗する。
///
/// [idlj-compile]: https://github.com/eclipse-ee4j/orb/blob/master/idlj/src/main/java/com/sun/tools/corba/ee/idl/Compile.java
public final class IdljCompiler extends Compile {
  /// `--idl-package-prefix`を指定しなかったときに、グローバルスコープの型を入れるパッケージ
  static final String DEFAULT_GLOBAL_PACKAGE = "generated";

  /// moduleとグローバルスコープの型に付けるパッケージ接頭辞。空なら付けない
  private final String packagePrefix;

  /// 構文解析で得たトップレベルの定義。解析に失敗したらnull
  private List<SymtabEntry> topLevel;

  /// 定義 → IDL上のスコープ付きの名前（パッケージ割り当て前に求めたもの）
  private Map<SymtabEntry, String> idlNames;

  private IdljCompiler(String packagePrefix) {
    this.packagePrefix = packagePrefix;
    // idljは処理中の自分自身をこの静的フィールドから参照する（本来はmainで設定される）
    Compile.compiler = this;
  }

  /// IDLファイルからスタブを生成し、モデルを返す
  ///
  /// @param idlFile 処理するIDLファイル（includeしたIDLの型も含めて処理する）
  /// @param stubRoot スタブの出力先
  /// @param packagePrefix moduleとグローバルスコープの型に付けるパッケージ接頭辞。空なら付けない
  /// @return モデル
  /// @throws GenerationException IDLを解析できなかった場合
  public static IdlModel compile(Path idlFile, Path stubRoot, String packagePrefix)
      throws GenerationException {
    Path idl = idlFile.toAbsolutePath();
    String[] args = {
      "-emitAll",
      "-fclient",
      "-i",
      idl.getParent().toString(),
      "-td",
      stubRoot.toAbsolutePath().toString(),
      idl.toString()
    };

    // idljはスタブのヘッダーに日時をロケールの書式で書き、日本語ロケールだとJDK 25では非推奨のタイムゾーンID「JST」の警告が
    // ファイルごとに出る。処理中だけ英語ロケールにする
    IdljCompiler compiler = new IdljCompiler(packagePrefix);
    Locale saved = Locale.getDefault();
    Locale.setDefault(Locale.ENGLISH);
    try {
      compiler.start(args);
    } finally {
      Locale.setDefault(saved);
    }

    if (compiler.topLevel == null) {
      throw new GenerationException("idljがIDLを解析できませんでした: " + idlFile);
    }
    return ModelBuilder.build(compiler.topLevel, compiler.idlNames);
  }

  /// 構文解析の直後、パッケージの割り当て前に呼ばれる
  ///
  /// idljはここで記号表にJavaのパッケージを書き込む。その前に、moduleとグローバルスコープの型の名前を`-pkgPrefix`の対象に足す。
  /// グローバルスコープの型はそのままだと無名パッケージに出力され、名前付きパッケージからimportできないため、必ずパッケージを与える。
  @Override
  protected void preEmit(Enumeration<SymtabEntry> entries) {
    topLevel = Collections.list(entries);
    // パッケージを割り当てると記号表の親の連なりに接頭辞のmoduleが差し込まれるので、IDL上の名前はその前に控えておく
    idlNames = ModelBuilder.idlNames(topLevel);
    Arguments args = (Arguments) arguments;
    for (SymtabEntry entry : topLevel) {
      if (entry instanceof ModuleEntry) {
        if (!packagePrefix.isEmpty()) args.packages.put(entry.name(), packagePrefix);
      } else if (isNamedType(entry)) {
        args.packages.put(
            entry.name(), packagePrefix.isEmpty() ? DEFAULT_GLOBAL_PACKAGE : packagePrefix);
      }
    }
    super.preEmit(Collections.enumeration(topLevel));
  }

  /// Javaのクラスになる、名前の付いた型の定義か（`#pragma`や無名のsequenceは除く）
  private static boolean isNamedType(SymtabEntry entry) {
    return entry instanceof TypedefEntry
        || entry instanceof StructEntry
        || entry instanceof EnumEntry
        || entry instanceof UnionEntry
        || entry instanceof InterfaceEntry
        || entry instanceof ForwardEntry;
  }
}
