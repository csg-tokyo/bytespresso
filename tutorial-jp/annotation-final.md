Bytespresso: Anotation @Final
==

仕様
--
- 対象: フィールド
- 記法: @Final

説明
--
@Finalアノテーションは、これを付与したフィールドが、変換後のCコード実行中に、変更されないということを示す。この情報はコード変換におけるconstant propagationのヒントとして使われる。

このアノテーションがついたフィールドはJava言語におけるfinal変数である必要はない。すなわち、そのフィールドはJava VMからは変更可能な変数として見える変数でも良い。だが変換されたCコードの中ではその変数の値を変更してはならない。

変換されたCコードの中で値が変更されないことは、プログラマが保証する。変換対象のJava Lambda式の中ではこの@Finalが付与された変数に書き込もうとするとエラーが吐かれる。

Javaのfinalとは別に、変換コードブロックの中での値の不変を指定できるようにした理由は、ライブラリを書いているとJavaのfinalの制約を緩和したいシチュエーションに出会うことがあるからである。例えば、フィールドのオプジェクトの初期化をconstructor以外の場所で行う必要があるクラスライブラリを書くことがあるが、そのオブジェクトが、初期化の後は不変であるなら、オブジェクトにfinalをつけられないが故に最適化が進まないという問題が発生する。@Finalはそのようなオブジェクトに用いると、パフォーマンスを発揮するための助けとなる。


利用例
--

mutableな変数flagA, flagBと、final修飾子によってimmutableになっている変数flagBがコード変換でどのように扱われるかを確かめるプログラムを以下に示す。

```Java
package sample;

import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;
import javassist.offload.Final;

public class AnnotationFinal {
	static boolean flagA;
	@Final static boolean flagB;
	final static boolean flagC = true;
    public static void main(String[] args) throws Exception {

    	flagA = true;
    	flagB = true;
    	
    	new StdDriver().invoke(() -> {
    		Util.print("Hello, Annotation Final");
    		Util.println();

    		if (flagA) {
    			Util.print("flagA is true");
        		Util.println();    			
    		} else {
    			Util.print("flagA is false");
        		Util.println();    			    			
    		}
    		
    		if (flagB) {
    			Util.print("flagB is true");
        		Util.println();    			
    		} else {
    			Util.print("flagB is false");
        		Util.println();    			    			
    		}

    		if (flagC) {
    			Util.print("flagC is true");
        		Util.println();    			
    		} else {
    			Util.print("flagC is false");
        		Util.println();    			    			
    		}

    	});    	
    }
}
```


以下は上記のプログラムを実行した時の出力である。

```
Hello, Annotation Final
flagA is true
flagB is true
flagC is true
```

Cコードに変換された後のJava Lambda式を以下に示す。flagA, flagB及びflagCは実行時には全てtrueであるが、flagB及びflagCは値がimmutableと判断され、if文が出力されない。特にflagBはJavaのコードとしてはmutableであるにもかかわらず、変換対象のLambda式の中ではimmutableと解釈されていることに注意が必要である。

```C
void AnnotationFinal_lambda_0_2() {

  Util_print_3(((struct java_string*)"\2\0\0\0\27\0\0\0" "Hello, Annotation Fina
l"));
  Util_println_6();
  if (AnnotationFinal_8_flagA_0 == 0) goto L1;
  Util_print_3(((struct java_string*)"\2\0\0\0\15\0\0\0" "flagA is true"));
  Util_println_6();
  goto L2;

 L1:
  Util_print_3(((struct java_string*)"\2\0\0\0\16\0\0\0" "flagA is false"));
  Util_println_6();

 L2:
  Util_print_3(((struct java_string*)"\2\0\0\0\15\0\0\0" "flagB is true"));
  Util_println_6();
  goto L4;

 L4:
  Util_print_3(((struct java_string*)"\2\0\0\0\15\0\0\0" "flagC is true"));
  Util_println_6();
  return ;

}
```
