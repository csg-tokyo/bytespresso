
Bytespresso: Annotation @Foreign
==

仕様
--
- 対象: static メソッド
	- 引数及び返り値の型はprimitiveな型またはその配列に限られる
- 記法: @Foreign

説明
--

@ForeignアノテーションはNativeコード呼び出しのための仕組みの一つである。BytespressoのCコードトランスレータは、このアノテーションが付与されたstatic methodと同名のC関数が（外部に）存在するものと仮定しCコード変換を行う。@Nativeアノテーションとは違って、@Foreignアノテーションの場合、function bodyの定義は出力されない。（@Nativeの場合は指定したNativeコードを包むC関数の定義、すなわちfunction bodyが出力される）
@Foreignアノテーションは外部のライブラリを直接呼び出すAPIを定義する時に使われる。呼び出す外部C関数のプロトタイプ宣言（ヘッダファイルなど）はStdDriverに代表されるBytespressoドライバクラスをカスタマイズして行う。外部のコンパイル済みライブラリのリンクが必要になる場合も、ドライバクラスのカスタマイズが必要になるため、@Foreignアノテーションを使うライブラリを開発するときに、多くの場合は、ドライバクラスを同時に開発する必要がある。
なお@Foreignアノテーションが付与されるメソッドは、staticでないといけないという制限がある。
> Note: 一般に、ホスト言語から違う言語のライブラリを呼び出す仕組みをForeign Function Interface (FFI)という。@ForeignはFFIを作るためのアノテーションである。


利用例
--

以下のコードはCの関数sqrtf及びgetpidをJavaから同名のJava関数として呼び出すサンプルコードである。
sqrtfのプロトタイプ宣言はmath.hにあるが、このヘッダはBytespressoがデフォルトでこれを読み込むCコードを出力するため、sqrtfは同名のJava関数に@Foreignアノテーションを付与するのみで使えるようになる。
一方、getpidのプロトタイプ宣言を含むunistd.hはデフォルトでは読み込まれるようにならないため、このサンプルコードではStdDriverをカスタマイズしたAnnotationForeignDriverを宣言し、利用している。このサンプルコードのように、preambleメソッドをオーバライドすることで、組み込むヘッダファイルの追加削除が可能である。

```Java
package sample;

import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;
import javassist.offload.Foreign;

class AnnotationForeignDriver extends StdDriver {

	@Override public String preamble() {
        return super.preamble() + "#include <unistd.h>\n";
    }
}


public class AnnotationForeign {
	
	@Foreign public static float sqrtf(float f) {
		return 0.0f;
	}
	
	@Foreign public static int getpid() {
		return 0;
	}
	
    public static void main(String[] args) throws Exception {
    	
    	new AnnotationForeignDriver().invoke(() -> {
    		Util.printer.p("Hello, Annotation Foreign").ln();
    		Util.printer.p("sqrtf(2.0f) = ").p(sqrtf(2.0f)).ln();
    		Util.printer.p("getpid() = ").p(getpid()).ln();
    	});    	
    }

}
```

サンプルコードの実行結果は次のようになる。

```
Hello, Annotation Foreign
sqrtf(2.0f) = 1.41421
getpid() = 55103
```


上記のコードにおける Java Lambda式は次のCコードに変換される。

```C
void AnnotationForeign_lambda_0_2() {

  Util_Printer_ln_7(Util_Printer_p_3_0(&gvar0, ((struct java_string*)"\2\0\0\0\31\0\0\0" "Hello, Annotation Foreign")));
  Util_Printer_ln_7(Util_Printer_p_9(Util_Printer_p_3_0(&gvar0, ((struct java_string*)"\2\0\0\0\16\0\0\0" "sqrtf(2.0f) = ")), sqrtf(2.0f)));
  Util_Printer_ln_7(Util_Printer_p_11(Util_Printer_p_3_0(&gvar0, ((struct java_string*)"\2\0\0\0\13\0\0\0" "getpid() = ")), getpid()));
  return ;

}
```

sqrtfやgetpidはC言語ライブラリを呼び出すものとしてCコードが出力されている。