Bytespresso: Hands on
===

Construct develop environment
--

ここではBytespressoライブラリ開発者のための開発環境の作り方を説明します。

###Prerequisites

本ドキュメント制作では以下の環境にてテストを行っています。
BytespressoはLinuxやWindowsなど他のプラットフォームでの稼働実績もあり、開発環境の構築手順は大きく変わりません。

- OS X El Capitan 10.11.6
- JDK 1.8
- Eclipse Neon Release 4.6.0
- Eclipse CDT 9.0.0
- M2E - Maven Integration for Eclipse 1.7.0

###Download

GithubからBytespressoソースを入手します。
https://github.com/csg-tokyo/bytespresso
この後の説明では展開したBytespressoソースのトップディレクトリのパスを$(BYTESPRESSO)と呼ぶことにします。

###Create Eclipse project

次の手順でEclipseのプロジェクトを設定します。

- File > New > Java Project ウィンドウを開く
- Project name を任意のものにする（ここではBytespressoとします）
- Use default locationのチェックを外す
- Locationとして$(BYTESPRESSO)を設定する
- Nextボタンを押します
- Librariesを選択
- Add Externel JARsボタンを押し、$(BYTESPRESSO)/javassist.jarを追加する
- Add Libraryボタンを押し、JUnit 4を追加する
- Finishを押す

###サンプルコードのコンパイルと実行

Bytespressoソースに含まれるサンプルコードを走らせてみることにします。

- Package Explorerで、Bytespressoのプロジェクトのsrc/test/array/Nbody.javaを右クリックしRun Configurationsを開く
- 左ペインで、Java Application/Nbody を選択
- ここで、Argumentsタブを開き、VM argumentsの中に次のオプションを設定しRunボタンで実行

```
-Djdk.internal.lambda.dumpProxyClasses=./bin
```

問題なければ、しばらく計算した後で、Consoleに次のような出力が出てきます。

```
103450msec, 1.7641823 GFlops
8760.028, 8760.028, 8760.028
```

###Maven Build

Eclipseに組み込まれているMavenを使って、Bytespressoのjarを作る方法を以下に示します。

- Package ExplorerでBytespressoパッケージを右クリックしRun Asの中のRun Configurationsを選ぶ
- Run Configurationsウィンドウの左ペインで、Maven Buildの下のNew_configuraionを選ぶ（New_configurationが存在しない場合は、Maven Buildをダブルクリック）
- Nameに適当な名前（たとえばBytespresso package）を入力
- Base directoryとしてFile Systemボタンを押して$(BYTESPRESSO)のディレクトリを入力
- Goalsにpackageと入力
- Runボタンを押す

Eclipseのコンソールに以下のようなメッセージが出ていればビルドに成功しています。

```
[INFO] 
[INFO] --- maven-jar-plugin:2.6:jar (default-jar) @ bytespresso ---
[INFO] Building jar: /Users/tosiyuki/src/bytespresso-master/target/bytespresso-1.0-GA.jar
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 3.808 s
[INFO] Finished at: 2016-10-05T18:07:04+09:00
[INFO] Final Memory: 20M/317M
[INFO] ------------------------------------------------------------------------
```

$(BYTESPRESSO)/targetディレクトリの中にbytespresso-1.0-GA.jarが出力されているはずです。


