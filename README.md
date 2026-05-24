# README <!-- omit in toc -->

## 目次 <!-- omit in toc -->

- [1. 概要](#1-概要)
- [2. 使い方](#2-使い方)
- [3. 注意点](#3-注意点)

## 1. 概要

このプロジェクトは、CORBAのIDLファイルからJavaスタブを生成し、さらにそれらのスタブからサービスインターフェースを抽出して、Spring Bootプロジェクトを自動生成するツールです。これにより、既存のCORBAベースのシステムをモダナイズする際の初期作業を大幅に削減できます。

## 2. 使い方

1. **IDLファイルの準備**: 解析したいIDLファイルを用意します。これらのファイルは、CORBAサービスのインターフェース定義を含んでいる必要があります。
2. **コマンドの実行**: 以下のコマンドを実行して、IDLファイルからJavaスタブを生成し、サービスインターフェースを抽出してSpring Bootプロジェクトを作成します。

```powershell
.\gradlew.bat run --console=plain --args="gen-spring --stub-root=generated-stubs --output-root=generated-spring --base-package=com.bridge --idl-package-prefix=com.corba --return-mode=raw idl/service.idl"
```

このコマンドの引数は以下の通りです：
- `--stub-root`: Javaスタブの出力先ディレクトリ。
- `--output-root`: 生成されたSpring Bootプロジェクトの出力先ディレクトリ。
- `--base-package`: 生成されるJavaコードのベースパッケージ。
- `--idl-package-prefix`: IDLファイルのパッケージプレフィックス。これにより、生成されるJavaスタブのパッケージ構造を制御できます。
- `--return-mode`: サービスインターフェースのメソッドの戻り値の形式を指定します。`raw` を指定すると、IDLで定義された型をそのまま使用します。`dto` を指定すると、戻り値をDTOクラスに変換します。
- `idl/service.idl`: 解析するIDLファイルのパス。

## 3. 注意点
- Javaスタブの生成には、JDKに含まれる`idlj`ツールが必要です。環境変数`JAVA_HOME`が正しく設定されていることを確認してください。
- 生成されたSpring Bootプロジェクトは、サービスインターフェースの定義に基づいて作成されますが、ビジネスロジックやデータアクセスコードは含まれていません。これらは手動で実装する必要があります。
- 生成されたコードは、プロジェクトの構造や命名規則に基づいていますが、必要に応じてカスタマイズしてください。
