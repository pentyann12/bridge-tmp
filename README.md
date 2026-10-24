# README <!-- omit in toc -->

## 目次 <!-- omit in toc -->

- [1. 概要](#1-概要)
- [2. 使い方](#2-使い方)
- [3. テスト](#3-テスト)
- [4. 注意点](#4-注意点)

## 1. 概要

このプロジェクトは、CORBAのIDLファイルからJavaスタブを生成し、さらにそれらのスタブからサービスインターフェースを抽出して、Spring Bootプロジェクトを自動生成するツールです。これにより、既存のCORBAベースのシステムをモダナイズする際の初期作業を大幅に削減できます。

## 2. 使い方

1. **IDLファイルの準備**: 解析したいIDLファイルを用意します。これらのファイルは、CORBAサービスのインターフェース定義を含んでいる必要があります。
2. **コマンドの実行**: 以下のコマンドを実行して、IDLファイルからJavaスタブを生成し、サービスインターフェースを抽出してSpring Bootプロジェクトを作成します。

```powershell
.\gradlew.bat run --console=plain --args="--stub-root=generated-stubs --output-root=generated-spring --base-package=com.bridge --idl-package-prefix=com.corba --return-mode=dto --single-project --project-name=BridgeServer idl/service.idl"
```

このコマンドの引数は以下の通りです：
- `--stub-root`: Javaスタブの出力先ディレクトリ。
- `--output-root`: 生成されたSpring Bootプロジェクトの出力先ディレクトリ。
- `--base-package`: 生成されるJavaコードのベースパッケージ。
- `--idl-package-prefix`: IDLファイルのパッケージプレフィックス。これにより、生成されるJavaスタブのパッケージ構造を制御できます。
- `--return-mode`: サービスインターフェースのメソッドの戻り値の形式を指定します。`raw` を指定すると、IDLで定義された型をそのまま使用します。`dto` を指定すると、戻り値をDTOクラスに変換します。
- `--single-project`: 全サービスを1つのSpring Bootプロジェクトにまとめます。指定しない場合はサービスごとに `<サービス名>Api` プロジェクトを生成します。
- `--project-name`: `--single-project` 時のプロジェクト名（既定値 `CombinedApi`）。
- `idl/service.idl`: 解析するIDLファイルのパス。

不明なオプションや不正な値を渡すと、使い方を表示して終了コード 2 で終了します。

内部の処理の流れは [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) を参照してください。

## 3. テスト

```powershell
.\gradlew.bat test
```

網羅用IDL（`src/test/resources/idl/coverage.idl`）から生成したプロジェクトを期待値（`src/test/resources/golden/`）と比較し、生成したソースが実際にコンパイルできることも確認します。生成ロジックを意図して変えたときは `.\gradlew.bat test -Dgolden.update=true` で期待値を更新し、差分をレビューしてください。

## 4. 注意点
- 実行にはJDK 25が必要です。ジェネレータはPATH上の`java`で`tools/idlj.jar`を起動してJavaスタブを生成します（JDK 11以降のJDKには`idlj`が含まれないため同梱しています）。
- 生成されるプロジェクトはSpring Boot 4.1・JDK 25・glassfish-corba-orb 5.0向けです。glassfish-corba-orb 5.0はgmbalの実装を別途必要とするため、生成される`build.gradle`には`org.glassfish.gmbal:gmbal`も含まれます。
- 生成されたSpring Bootプロジェクトは、サービスインターフェースの定義に基づいて作成されますが、ビジネスロジックやデータアクセスコードは含まれていません。これらは手動で実装する必要があります。
- 生成されたコードは、プロジェクトの構造や命名規則に基づいていますが、必要に応じてカスタマイズしてください。
