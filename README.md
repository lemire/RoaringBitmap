RoaringBitmap
=============

[![Build Status](https://travis-ci.org/RoaringBitmap/RoaringBitmap.png)](https://travis-ci.org/RoaringBitmap/RoaringBitmap)
[![][maven img]][maven]
[![][license img]][license]
[![docs-badge][]][docs]
[![Coverage Status](https://coveralls.io/repos/github/RoaringBitmap/RoaringBitmap/badge.svg?branch=master)](https://coveralls.io/github/RoaringBitmap/RoaringBitmap?branch=master)

Bitsets, also called bitmaps, are commonly used as fast data structures.
Unfortunately, they can use too much memory. To compensate, we often use
compressed bitmaps.

Roaring bitmaps are compressed bitmaps which tend to outperform conventional
compressed bitmaps such as WAH, EWAH or Concise. In some instances, roaring bitmaps can
be hundreds of times faster and they often offer significantly better compression.
They can even be faster than uncompressed bitmaps.

Roaring bitmaps are found to work well in many important applications:

> Use Roaring for bitmap compression whenever possible. Do not use other bitmap compression methods ([Wang et al., SIGMOD 2017](http://db.ucsd.edu/wp-content/uploads/2017/03/sidm338-wangA.pdf))

> kudos for making something that makes my software run 5x faster (Charles Parker from BigML)

This library is used by
*   [Apache Spark](http://spark.apache.org/),
*   [Apache Hive](http://hive.apache.org),
*   [Apache Tez](http://tez.apache.org),
*   [Apache Kylin](http://kylin.io),
*   [Apache CarbonData](https://carbondata.apache.org),
*   [Netflix Atlas](https://github.com/Netflix/atlas),
*   [OpenSearchServer](http://www.opensearchserver.com),
*   [zenvisage](http://zenvisage.github.io/),
*   [Jive Miru](https://github.com/jivesoftware/miru),
*   [Tablesaw](https://github.com/jtablesaw/tablesaw),
*   [Apache Hivemall](http://hivemall.incubator.apache.org),
*   [Gaffer](https://github.com/gchq/Gaffer),
*   [LinkedIn Pinot](https://github.com/linkedin/pinot/wiki) and
*   [Apache Druid](https://druid.apache.org/).


[Apache Lucene](http://lucene.apache.org/) uses  Roaring bitmaps, though they have their own [independent implementation](https://svn.apache.org/viewvc/lucene/dev/branches/branch_5x/lucene/core/src/java/org/apache/lucene/util/RoaringDocIdSet.java?view=markup&pathrev=1629606). Derivatives of Lucene such as Solr and Elastic also use Roaring bitmaps.
Other platforms such as [Whoosh](https://pypi.python.org/pypi/Whoosh/), [Microsoft Visual Studio Team Services (VSTS)](https://www.visualstudio.com/team-services/) and [Pilosa](https://github.com/pilosa/pilosa) also use Roaring bitmaps with their own implementations. You find Roaring bitmaps in [InfluxDB](https://www.influxdata.com), [Bleve](http://www.blevesearch.com), [Cloud Torrent](https://github.com/jpillora/cloud-torrent), and so forth.


There is a serialized format specification for interoperability between implementations: https://github.com/RoaringBitmap/RoaringFormatSpec/
We have interoperable [C/C++](https://github.com/RoaringBitmap/CRoaring), Java and [Go](https://github.com/RoaringBitmap/roaring) implementations.

(c) 2013-2017 the RoaringBitmap authors


This code is licensed under Apache License, Version 2.0 (AL2.0).



When should you use a bitmap?
===================================


Sets are a fundamental abstraction in
software. They can be implemented in various
ways, as hash sets, as trees, and so forth.
In databases and search engines, sets are often an integral
part of indexes. For example, we may need to maintain a set
of all documents or rows  (represented by numerical identifier)
that satisfy some property. Besides adding or removing
elements from the set, we need fast functions
to compute the intersection, the union, the difference between sets, and so on.


To implement a set
of integers, a particularly appealing strategy is the
bitmap (also called bitset or bit vector). Using n bits,
we can represent any set made of the integers from the range
[0,n): the ith bit is set to one if integer i is present in the set.
Commodity processors use words of W=32 or W=64 bits. By combining many such words, we can
support large values of n. Intersections, unions and differences can then be implemented
 as bitwise AND, OR and ANDNOT operations.
More complicated set functions can also be implemented as bitwise operations.

When the bitset approach is applicable, it can be orders of
magnitude faster than other possible implementation of a set (e.g., as a hash set)
while using several times less memory.

However, a bitset, even a compressed one is not always applicable. For example, if the
you have 1000 random-looking integers, then a simple array might be the best representation.
We refer to this case as the "sparse" scenario.

When should you use compressed bitmaps?
===================================

An uncompressed BitSet can use a lot of memory. For example, if you take a BitSet
and set the bit at position 1,000,000 to true and you have just over 100kB. That's over 100kB
to store the position of one bit. This is wasteful  even if you do not care about memory:
suppose that you need to compute the intersection between this BitSet and another one
that has a bit at position 1,000,001 to true, then you need to go through all these zeroes,
whether you like it or not. That can become very wasteful.

This being said, there are definitively cases where attempting to use compressed bitmaps is wasteful.
For example, if you have a small universe size. E.g., your bitmaps represent sets of integers
from [0,n) where n is small (e.g., n=64 or n=128). If you are able to uncompressed BitSet and
it does not blow up your memory usage,  then compressed bitmaps are probably not useful
to you. In fact, if you do not need compression, then a BitSet offers remarkable speed.

The sparse scenario is another use case where compressed bitmaps should not be used.
Keep in mind that random-looking data is usually not compressible. E.g., if you have a small set of
32-bit random integers, it is not mathematically possible to use far less than 32 bits per integer,
and attempts at compression can be counterproductive.

How does Roaring compares with the alternatives?
==================================================


Most alternatives to Roaring are part of a larger family of compressed bitmaps that are run-length-encoded
bitmaps. They identify long runs of 1s or 0s and they represent them with a marker word.
If you have a local mix of 1s and 0, you use an uncompressed word.

There are many formats in this family:

* Oracle's BBC is an obsolete format at this point: though it may provide good compression,
it is likely much slower than more recent alternatives due to excessive branching.
* WAH is a patented variation on BBC that provides better performance.
* Concise is a variation on the patented WAH. It some specific instances, it can compress
much better than WAH (up to 2x better), but it is generally slower.
* EWAH is both free of patent, and it is faster than all the above. On the downside, it
does not compress quite as well. It is faster because it allows some form of "skipping"
over uncompressed words. So though none of these formats are great at random access, EWAH
is better than the alternatives.



There is a big problem with these formats however that can hurt you badly in some cases: there is no random access. If you want to check whether a given value is present in the set, you have to start from the beginning and "uncompress" the whole thing. This means that if you want to intersect a big set with a large set, you still have to uncompress the whole big set in the worst case...

Roaring solves this problem. It works in the following manner. It divides the data into chunks of 2<sup>16</sup> integers
(e.g., [0, 2<sup>16</sup>), [2<sup>16</sup>, 2 x 2<sup>16</sup>), ...). Within a chunk, it can use an uncompressed bitmap, a simple list of integers,
or a list of runs. Whatever format it uses, they all allow you to check for the present of any one value quickly
(e.g., with a binary search). The net result is that Roaring can compute many operations much faster that run-length-encoded
formats like WAH, EWAH, Concise... Maybe surprisingly, Roaring also generally offers better compression ratios.



API docs
---------

http://www.javadoc.io/doc/org.roaringbitmap/RoaringBitmap/

Scientific Documentation
--------------------------
- Daniel Lemire, Owen Kaser, Nathan Kurz, Luca Deri, Chris O'Hara, François Saint-Jacques, Gregory Ssi-Yan-Kai, Roaring Bitmaps: Implementation of an Optimized Software Library, Software: Practice and Experience 48 (4), 2018 [arXiv:1709.07821](https://arxiv.org/abs/1709.07821)
-  Samy Chambi, Daniel Lemire, Owen Kaser, Robert Godin, Better bitmap performance with Roaring bitmaps,
Software: Practice and Experience Volume 46, Issue 5, pages 709–719, May 2016 http://arxiv.org/abs/1402.6407 This paper used data from http://lemire.me/data/realroaring2014.html
- Daniel Lemire, Gregory Ssi-Yan-Kai, Owen Kaser, Consistently faster and smaller compressed bitmaps with Roaring, Software: Practice and Experience 46 (11), 2016. http://arxiv.org/abs/1603.06549
- Samy Chambi, Daniel Lemire, Robert Godin, Kamel Boukhalfa, Charles Allen, Fangjin Yang, Optimizing Druid with Roaring bitmaps, IDEAS 2016, 2016. http://r-libre.teluq.ca/950/

Code sample
-------------

```java        
import org.roaringbitmap.RoaringBitmap;

public class Basic {

  public static void main(String[] args) {
        RoaringBitmap rr = RoaringBitmap.bitmapOf(1,2,3,1000);
        RoaringBitmap rr2 = new RoaringBitmap();
        rr2.add(4000L,4255L);
        rr.select(3); // would return the third value or 1000
        rr.rank(2); // would return the rank of 2, which is index 1
        rr.contains(1000); // will return true
        rr.contains(7); // will return false

        RoaringBitmap rror = RoaringBitmap.or(rr, rr2);// new bitmap
        rr.or(rr2); //in-place computation
        boolean equals = rror.equals(rr);// true
        if(!equals) throw new RuntimeException("bug");
        // number of values stored?
        long cardinality = rr.getLongCardinality();
        System.out.println(cardinality);
        // a "forEach" is faster than this loop, but a loop is possible:
        for(int i : rr) {
          System.out.println(i);
        }
  }
}
```

Please see the examples folder for more examples, which you can run with `./gradlew :examples:runAll`, or run a specific one with `./gradlew :examples:runExampleBitmap64`, etc.


Unsigned integers
------------------

Java lacks native unsigned integers but integers are still considered to be unsigned within Roaring and ordered according to ``Integer.compareUnsigned``. This means that Java will order the numbers like so 0, 1, ..., 2147483647, -2147483648, -2147483647,..., -1. To interpret correctly, you can use ``Integer.toUnsignedLong`` and ``Integer.toUnsignedString``.


Working with memory-mapped bitmaps
---------------------------------------

If you want to have your bitmaps lie in memory-mapped files, you can
use the org.roaringbitmap.buffer package instead. It contains two
important classes, ImmutableRoaringBitmap and MutableRoaringBitmap.
MutableRoaringBitmaps are derived from ImmutableRoaringBitmap, so that
you can convert (cast) a MutableRoaringBitmap to an ImmutableRoaringBitmap
in constant time.

An ImmutableRoaringBitmap that is not an instance of a MutableRoaringBitmap
is backed by a ByteBuffer which comes with some performance overhead, but
with the added flexibility that the data can reside anywhere (including outside
of the Java heap).

At times you may need to work with bitmaps that reside on disk (instances
of ImmutableRoaringBitmap) and bitmaps that reside in Java memory. If you
know that the bitmaps will reside in Java memory, it is best to use
MutableRoaringBitmap instances, not only can they be modified, but they
will also be faster. Moreover, because MutableRoaringBitmap instances are
also ImmutableRoaringBitmap instances, you can write much of your code
expecting ImmutableRoaringBitmap.

If you write your code expecting ImmutableRoaringBitmap instances, without
attempting to cast the instances, then your objects will be truly immutable.
The MutableRoaringBitmap has a convenience method (toImmutableRoaringBitmap)
which is a simple cast back to an ImmutableRoaringBitmap instance.
From a language design point of view, instances of the ImmutableRoaringBitmap class are immutable only when used as per
the interface of the ImmutableRoaringBitmap class. Given that the class is not final, it is possible
to modify instances, through other interfaces. Thus we do not take the term "immutable" in a purist manner,
but rather in a practical one.

One of our motivations for this design where MutableRoaringBitmap instances can be casted
down to ImmutableRoaringBitmap instances is that bitmaps are often large,
or used in a context where memory allocations are to be avoided, so we avoid forcing copies.
Copies could be expected if one needs to mix and match ImmutableRoaringBitmap and MutableRoaringBitmap instances.

The following code sample illustrates how to create an ImmutableRoaringBitmap
from a ByteBuffer. In such instances, the constructor only loads the meta-data
in RAM while the actual data is accessed from the ByteBuffer on demand.

```java
        import org.roaringbitmap.buffer.*;

        //...

        MutableRoaringBitmap rr1 = MutableRoaringBitmap.bitmapOf(1, 2, 3, 1000);
        MutableRoaringBitmap rr2 = MutableRoaringBitmap.bitmapOf( 2, 3, 1010);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        // If there were runs of consecutive values, you could
        // call rr1.runOptimize(); or rr2.runOptimize(); to improve compression
        rr1.serialize(dos);
        rr2.serialize(dos);
        dos.close();
        ByteBuffer bb = ByteBuffer.wrap(bos.toByteArray());
        ImmutableRoaringBitmap rrback1 = new ImmutableRoaringBitmap(bb);
        bb.position(bb.position() + rrback1.serializedSizeInBytes());
        ImmutableRoaringBitmap rrback2 = new ImmutableRoaringBitmap(bb);
```

Alternatively, we can serialize directly to a `ByteBuffer` with the `serialize(ByteBuffer)` method.


Operations on an ImmutableRoaringBitmap such as and, or, xor, flip, will
generate a RoaringBitmap which lies in RAM. As the name suggest, the
ImmutableRoaringBitmap itself cannot be modified.


This design was inspired by Druid.

One can find a complete working example in the test file TestMemoryMapping.java.

Note that you should not mix the classes from the org.roaringbitmap package with the classes
from the org.roaringbitmap.buffer package. They are incompatible. They serialize
to the same output however. The performance of the code in org.roaringbitmap package is
generally superior because there is no overhead due to the use of ByteBuffer instances.


Kryo
-----

Many applications use Kryo for serialization/deserialization. One can
use Roaring bitmaps with Kryo efficiently thanks to a custom serializer (Kryo 5):

```java
public class RoaringSerializer extends Serializer<RoaringBitmap> {
    @Override
    public void write(Kryo kryo, Output output, RoaringBitmap bitmap) {
        try {
            bitmap.serialize(new KryoDataOutput(output));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }
    @Override
    public RoaringBitmap read(Kryo kryo, Input input, Class<? extends RoaringBitmap> type) {
        RoaringBitmap bitmap = new RoaringBitmap();
        try {
            bitmap.deserialize(new KryoDataInput(input));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
        return bitmap;
    }

}
```

64-bit integers (long)
-----------------------

Though Roaring Bitmaps were designed with the 32-bit case in mind, we have an extension to 64-bit integers:

```
      import org.roaringbitmap.longlong.*;

      LongBitmapDataProvider r = Roaring64NavigableMap.bitmapOf(1,2,100,1000);
      r.addLong(1234);
      System.out.println(r.contains(1)); // true
      System.out.println(r.contains(3)); // false
      LongIterator i = r.getLongIterator();
      while(i.hasNext()) System.out.println(i.next());
```

Prerequisites
-------------


 - Version 0.7.x requires JDK 8 or better
 - Version 0.6.x requires JDK 7 or better
 - Version 0.5.x requires JDK 6 or better

To build the project you need maven (version 3).


Download
---------

You can download releases from the Maven repository:
http://central.maven.org/maven2/org/roaringbitmap/RoaringBitmap/

or from github:
https://github.com/RoaringBitmap/RoaringBitmap/releases

Maven repository
----------------
If your project depends on roaring, you  can  specify the dependency in the Maven "pom.xml" file:

```xml
        <dependencies>
          <dependency>
            <groupId>org.roaringbitmap</groupId>
            <artifactId>RoaringBitmap</artifactId>
            <version>[0.6,)</version>
          </dependency>
        </dependencies>
```

where you should replace the version number by the version you require.

Usage
------

* Get java

* ``./gradlew assemble`` will compile
* ``./gradlew build`` will compile and run the  unit tests
* ``./gradlew test `` will run the tests
* ``./gradlew checkstyleMain`` will check that you abide by the code style and that the code compiles. We enforce a strict style so that there is no debate as to the proper way to format the code.


IntelliJ and Eclipse
--------

If you plan to contribute to RoaringBitmap, you can have load
it up in your favorite IDE.
- For IntelliJ, in the IDE create a new project, possibly from existing sources, choose import, gradle.
- For Eclipse: File, Import, Existing Gradle Projects, Select RoaringBitmap on my disk

Contributing
------------

Contributions are invited. We enforce the Google Java style.
Please run  ``./gradlew checkstyleMain`` on your code before submitting
a patch.

FAQ
----

* I am getting an error about a bad cookie. What is this about?

In the serialized files, part of the first 4 bytes are dedicated to a "cookie"
which serves to indicate the file format.

If you try to deserialize or map a bitmap from data that has an
unrecognized "cookie", the code will abort the process and report
an error.

This problem will occur to all users who serialized Roaring bitmaps
using versions prior to 0.4.x as they upgrade to version 0.4.x or better.
These users need to refresh their serialized bitmaps.

* How big can a Roaring bitmap get?

Given N integers in [0,x), then the serialized size in bytes of
a Roaring bitmap should never exceed this bound:

`` 8 + 9 * ((long)x+65535)/65536 + 2 * N ``

That is, given a fixed overhead for the universe size (x), Roaring
bitmaps never use more than 2 bytes per integer. You can call
``RoaringBitmap.maximumSerializedSize`` for a more precise estimate.

* What is the worst case scenario for Roaring bitmaps?

There is no such thing as a data structure that is always ideal. You should
make sure that Roaring bitmaps fit your application profile.
There are at least two cases where Roaring bitmaps can be easily replaced
by superior alternatives compression-wise:

1. You have few random values spanning in a large interval (i.e., you have
a very sparse set). For example, take the set 0, 65536, 131072, 196608, 262144 ...
If this is typical of your application, you might consider using a HashSet or
a simple sorted array.

2. You have dense set of small random values that never form runs of continuous
values. For example, consider the set 0,2,4,...,10000. If this is typical of your
application, you might be better served with a conventional bitset (e.g., Java's BitSet class).

* How do I select an element at random?

         Random random = new Random();
         bitmap.select(random.nextInt(bitmap.getCardinality()));


Benchmark
-----------

To run JMH benchmarks, use the following command:

         $ ./gradlew jmh

You can also run specific benchmarks...

         $ ./jmh/run.sh 'org.roaringbitmap.aggregation.and.identical.*'


Optimization tips
-----------------------------


- [Some performance tricks when using the RoaringBitmap library](https://richardstartin.github.io/posts/roaringbitmap-performance-tricks)


Mailing list/discussion group
-----------------------------

https://groups.google.com/forum/#!forum/roaring-bitmaps

Funding
----------

This work was supported by NSERC grant number 26143.



[maven img]:https://maven-badges.herokuapp.com/maven-central/org.roaringbitmap/RoaringBitmap/badge.svg
[maven]:http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.roaringbitmap%22%20

[license]:LICENSE-2.0.txt
[license img]:https://img.shields.io/badge/License-Apache%202-blue.svg

[docs-badge]:https://img.shields.io/badge/API-docs-blue.svg?style=flat-square
[docs]:http://www.javadoc.io/doc/org.roaringbitmap/RoaringBitmap/
