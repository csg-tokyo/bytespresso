
Bytespresso: Tutorial 1
==

[TOC]

## Hello World

### プログラムのコンパイルと実行

"Bytespresso: Hands on" などを参考にBytespressoの開発環境が構築済みであることを前提とします。

Bytespressoで"Hello, World!"プログラムは次のように書くことができます。このコードは、本来JVMが解釈するlambda式をC言語に変換し、コンパイルして実行するものです。変換とコンパイル、実行はBytespressoが提供するStdDriverオブジェクトのinvokeメソッドが行います。

```java:HelloWorld.java
import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;

public class HelloWorld {
    public static void main(String[] args) throws Exception {
    	new StdDriver().invoke(() -> {
    		Util.printer.p("Hello, World!").ln();
    	});    	
    }    
}
```

まず上のコードを、`HelloWorld.java`というファイル名で保存してください。もちろん、どこのディレクトリに保存しても自由ですが、カレントディレクトリに保存されている前提で以下の説明を進めます。

`HelloWorld.java`は普通のJavaコードとしてコンパイルします。この時、CLASSPATHに`$(BYTESPRESSO)/bin`と、`javasssist.jar`が含まれている必要があります。

```sh
$ javac HelloWorld.java
$ ls 
HelloWorld.class	HelloWorld.java
```

コンパイル結果である`HelloWorld.class`を実行する際にはjdk.internal.lambda.dumpProxyClassesをオプションを設定する必要があります。このオプションで設定する「lambda式のProxyClassを吐き出す先」は、CLASSPATHが通っているディレクトリである必要があります。

```sh
$ java -Djdk.internal.lambda.dumpProxyClasses=. HelloWorld
Hello, World!
```

無事に `Hello, World!`が出力されました。invoke メソッドの引数で渡された lambda 式は、 C コードに変換、コンパイルされて JVM の外で実行されています。JVM から native な CPU へ処理が「オフロードされた」のです。

一般的にJavaでは文字列の出力に`System.out.println`メソッドを使用しますが、変換対象のlambda式では`Util.printer.p`メソッド及び`Util.printer.ln`メソッドを使います。

上記のサンプルプログラムは`src/test/sample/HelloWorld.java`としてBytespressoのソースコード一式に含まれています。

EclipseでBytespressoのソースコードをJavaプロジェクトとして開ける状態にしてあれば、Run As > Java Applicationで`HelloWorld.java`をEclipseから実行できます。この時、JVMの引数はRun Configurationsウィンドウを開いて次のように設定します。
![JVM option to run Bytespreeso code](img/jvm-option.png?raw=true)

###生成される中間ファイル

ここで、上記のHelloWorld.classを実行した際、次のようなファイルが吐き出されているのを確認することができます。`bytespresso.c`、`a.out`はjavaコマンドを実行した時のディレクトリ（Eclipseの場合はプロジェクトのトップディレクトリ）に出力されます。`HelloWorld$$Lambda$1.class`及び`java`ディレクトリは`-Djdk.internal.lambda.dumpProxyClasses`が指すディレクトリに出力されます。


```sh
$ ls -F
HelloWorld$$Lambda$1.class	HelloWorld.java			bytespresso.c
HelloWorld.class		a.out*				java/
```

`HelloWorld.java`ソースでが、`StdDriver.invoke`メソッドにlambda式を渡していますが、このlambda式がC言語に変換されたものが`bytespresso.c`で、これをコンパイルしたバイナリが`a.out`です。`a.out`を単体で実行すると、`Hello, World!`出力が得られます。

```sh
$ ./a.out
Hello, World!
```

このほか、`HelloWorld.java`をコンパイルした時には存在しなかったファイル`HelloWorld$$Lambda$1.class`やディレクトリ`java`を見つけることができると思います。`java`ディレクトリの中を見ると`Lambda`という名前の付いた`class`ファイルが確認できます。

これらのファイルは、lambda式をコンパイルするにあたって、処理系が吐き出したlambda式のバイトコード（classファイル）です。

`bytespresso.c`や`a.out`が吐き出されるディレクトリは変更することが可能です。方法については後述します。

###どんなlambda式が変換できるの？

現状BytespressoがCコードに変換できるlambda式には制限があります。制限については別途記述します。変換においてStringの扱いに制限が出てしまう都合、残念ながら多くのStandard Java APIは使えません。

`HelloWorld.java`において、文字列の出力に`System.out.println`メソッドが使われず、代わりに`Util.printer.p`メソッド及び`Util.printer.ln`メソッドが使われていますが、これは、変換対象のlambda式に`System.out.println`メソッドが使えないという制限からくるものです。

変換対象となるlambda式はJava言語で書きます。lambda式から呼ばれるメソッドもまた、変換対象となります。このJavaメソッドにアノテーションをつけることで、コードをCのNative関数に置き換えたり、JVMへ実行を依頼したりすることができます。

例えば、`Util.print`メソッドは`@Native`アノテーションでCのNative関数に置き換えられています。（下記のコードは現状のライブラリ実装とは異なります）

```Java
@Native("fprintf(stdout, \"%d\", (int)v1); return 0;")
public static Util print(int i) {
    System.err.print(i);
    return null;
}
```

現状のBytespressoの利用者の多くは、Bytespresso向けのアノテーションが含まれるJavaライブラリ（Bytespressoライブラリ）を組むところからコーディングしていく必要があります。

Bytespressoを利用する利点の一つは、Javaコードとしての見た目を維持しつつ、実際の実行はCコードで行うというBytespressoライブラリを 利用することができるということです。Bytespressoライブラリの差し替えで、ターゲットマシンやMPIやCUDAといったランタイムの変更もできるようになります。

JavaからCのNative関数を利用する方法としてJNIがあります。JNIとBytespressoの違いは、JNIが単にメソッド呼び出し先がNativeコードになるのに対し、Bytespressoでは、lambda式呼び出しの単位での変換ができるというところにあります。JNIでは抽象化しづらかった、並列ライブラリ記述がより簡単なるほか、より多くの情報を用いた最適化を行うことが可能になります。

###lambda式をリモートマシンで実行

コンパイル方法やバイナリの実行方法はJavaのProperty `c.compiler` 及び`c.exec` を設定し、変更することができます。

このPropertyの設定で、Cコードに変換されたlambda式を、ローカルマシンよりも高性能なリモートマシンで実行することができます。

事前に次のようなスクリプト`compile.sh`と`run.sh`をローカルマシンに作っておきます。リモートマシンは`playground`でローカルマシンからはパスフレーズなしでログインできるという前提です。

```sh
# compile.sh
scp bytespresso.c playground:
ssh playground "source .profile; cc -O3 bytespresso.c"

# run.sh
ssh playground "source .profile; ./a.out"
```

ローカルマシンでHelloWorld.classを実行すると、リモートマシンに変換済みの`bytespresso.c`がコピーされ、リモートマシン上にバイナリが作られます。実行はリモートマシンで行われ、結果がローカルマシンに戻ります。

```sh
$ java -Djdk.internal.lambda.dumpProxyClasses=. -Dc.compiler=./compile.sh -Dc.exec=./run.sh HelloWorld
Hello, World!
```


Hello World on MPI processes
--

これまでの例では`StdDriver`がCコードへの変換、コンパイル、及びバイナリの実行を担っていました。Bytespressoは`StdDriver`をカスタマイズする方法を提供しています。これによりCコードの変換方法を変更することができます。カスタマイズの一例として`MPIDriver`があります。これは、JavaでのMPIプログラミングを可能にするものです。`MPIDriver`はBytespressoの配布ソース中に含まれています。この節では`MPIDriver`を例に`StdDriver`のカスタマイズ事例を見ていきます。（C言語でのMPIプログラミングについて多少の知識があることを前提とします）

###事前準備

ローカルマシンにMPICHなどのMPI処理系をインストールします。`mpicc` や`mpiexec`にパスが通っている状態にしておきます。

###MPI版Hello World

以下のコードを`HelloMPI.java`という名前で保存します。このコードは、`MPIDriver.invoke`メソッドの引数になっているlambda式をMPIプログラムとして実行するものです。

```java:HelloMPI.java
import javassist.offload.Options;
import javassist.offload.lib.MPI;
import javassist.offload.lib.MPIDriver;
import javassist.offload.lib.Util;

public class HelloMPI {
    public static void main(String[] args) throws Exception {
        main();
    }

    public static void main() throws Exception {
        Options.portableInitialization = true;
        
        new MPIDriver(4).invoke(() -> {
        	int rank;
        	int procs;
        	rank = MPI.commRank();
        	procs = MPI.commSize();
        	for (int i = 0; i < procs; i++) {
        		MPI.barrier();
        		if (i == rank) {
	        		Util.printer.p("Hello ").p(MPI.commRank()).ln();
        		}
        	}
        });        
    }
}
```
`MPIDriver`は`StdDriver`をカスタマイズしたものです。`MPIDriver`コンストラクタの引数`4`は`mpiexec`の`-n`オプションとして渡される実行時のノード数です。

lambda式の中身に目を移すと、`MPI.commRank` `MPI.commSize` `MPI.barrier` といったメソッドの呼び出しの存在に気がつきます。想像つく通り、MPIのC言語インターフェースである`MPI_Comm_rank` `MPI_Comm_size` `MPI_Barrier`をJavaでラップしたものです。どのように変換されているかは後で確認します。

lambda式部分は、実行時にはMPIプロセスとして各ノードで実行されます。このlambda式はMPIプロセスの数(=`procs`)だけループを回し、自分の番が回ってきた時、`Hello`を出力するコードです。ループの先頭でバリア同期を取っていますが、これは出力が順序良く行われるように順番を制御するためのものです。

###コンパイルと実行

次のように、`HelloMPI.java`をコンパイルし、実行することができます。

```sh
$ javac HelloMPI.java
$ java -Djdk.internal.lambda.dumpProxyClasses=. HelloMPI
Hello 0
Hello 1
Hello 2
Hello 3
```

上記のサンプルプログラムは`src/test/sample/HelloMPI.java`としてBytespressoのソースコード一式に含まれています。

EclipseでBytespressoのソースコードをJavaプロジェクトとして開ける状態にしてあれば、Run As > Java Applicationで`HelloMPI.java`をEclipseから実行できます。この時、JVMの引数は`HelloWorld.java`の時と同様に、Run Configurationsウィンドウを開いてArgumentsタブを次のように設定します。
![JVM option to run Bytespreeso code](img/jvm-option.png?raw=true)

さらにEnvironmentタブでmpiccへのパスを通し、MPIランタイムのためのダイナミックリンクライブラリのパスを通します。設定するパスは実行環境に依存することとなります。
![JVM environment to run Bytespreeso code](img/jvm-environment.png?raw=true)

lambda式部分は`HelloMPI`実行時にCコード`bytespresso.c`に変換されます。このコードはMPIプログラムとして`mpicc`でコンパイルされ、`mpiexec`で実行されます。この一連の動きをMPIDriverが担っています。

デフォルトでは、`mpiexec`のオプションは`-n`だけで実行され、ホストファイルなどの設定はありません。`MPIDriver`コンストラクタの引数として埋め込まれていたノード数`4`だけが設定され、ローカルマシン内にMPIプロセスが4つ立ち上がることになります。

> プログラムの外から実行時のノード数を変更したり、ホストファイルを設定したりする必要があるときには `c.exec`プロパティに文字列`mpiexec -n <number> -f <hostfile>` を設定します。

### lambda式のMPIプログラムへの変換

`StdDriver`はlambda式を（単体のマシンで動かす前提の）Cのコードに変換するものでしたが、`MPIDriver`はlambda式をMPIプログラムとして複数のノードで動かすためのCコードに変換します。

これにはMPIのC関数`MPI_Init`や`MPI_Finalize`をCコードの中に挿入する必要があります。

挿入する文字列は`javassist.offload.lib.MPIDriver`の以下のメソッドに書かれています。

```java
    @Override public String prologue() { return " MPI_Init(&argc, &argv);\n"; }
    @Override public String epilogue() { return " MPI_Finalize();\n"; }
```

吐き出されたbytespresso.cを見ると、次のように`MPI_Init`と`MPI_Finalize`が埋め込まれていることがわかります。

```c
int main(int argc, char** argv) {
  MPI_Init(&argc, &argv);
  int result = StandaloneDriver_runVoid_0_0(&gvar0);
  MPI_Finalize();
  return result;
}
```

###MPI関数呼び出し

先に`MPI.commRank` `MPI.commSize` `MPI.barrier` といったメソッドの呼び出しは、MPIのC言語インターフェースである`MPI_Comm_rank` `MPI_Comm_size` `MPI_Barrier`をJavaでラップしたものと書きましたが、具体的にどのように変換されているかを`MPI.barrier`を例に見ていきます。

Javaメソッド`MPI.barrier`は`javassist.offload.lib.MPI`クラスで定義されています。

```java
    @Native("MPI_Barrier(MPI_COMM_WORLD);")
    public static void barrier() {
        MPIRuntime.barrier();
    }
```

ここで、`MPIRuntime.barrier`メソッドは`MPI.barrier`メソッドを普通のJVMで実行した時に呼ばれるものなのですが、ここでは無視して大丈夫です。Bytespressoでlambda式変換が行われるときは`@Native`アノテーションで指定されている文字列がCコードとして出力されます。

> `MPIRuntime`クラスはJVMのスレッドでMPIプロセス、MPI通信をエミュレートする仕組みです。

変換後のコードは`bytespresso.c`で確認することができます。
以下のような関数に変換されています。

```c
/* native */ void MPI_barrier_5() {
  MPI_Barrier(MPI_COMM_WORLD);
}
```

`MPI.barrier`メソッドの呼び出し元となっていた（変換対象の）lambda式は次のように変換されています。

```c
void HelloMPI_lambda_0_2() {
 int v0;
 int v1;
 int v2;

  v0 = MPI_commRank_3();
  v1 = MPI_commSize_4();

 L1:
  for (v2 = 0; v2 < v1; ++v2) {
  MPI_barrier_5();
  if (v2 != v0) goto L2;
  Util_Printer_ln_12(Util_Printer_p_10(Util_Printer_p_6_0(&gvar1, ((struct java_string*)"\2\0\0\0\6\0\0\0" "Hello ")), MPI_commRank_3()));

 L2:
  ;

 L3:
  ; } /* for L1 */
  return ;

}
```