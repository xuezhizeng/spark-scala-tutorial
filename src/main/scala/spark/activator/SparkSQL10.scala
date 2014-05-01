package spark.activator

import spark.activator.util.{Timestamp, CommandLineOptions}
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext


/** 
 * We'll load the verses from the King James Version of the Bible again.
 * Recall that each line has the format:
 *   book|chapter|verse| text.~
 * We use a case class to define the schema.
 */
case class Verse(book: String, chapter: Int, verse: Int, text: String)

/** Example of Spark SQL, using the KJV Bible text. */
object SparkSQL10 {
  def main(args: Array[String]) = {

    val options = CommandLineOptions(
      defaultInputPath  = "data/kjvdat.txt",
      defaultOutputPath = "output/kjv-queries",
      defaultMaster     = "local",
      programName       = this.getClass.getSimpleName)

    val argz = options(args.toList)
    val sc = new SparkContext(argz.master, "Spark SQL (6)")
    val sqlContext = new SQLContext(sc)
    import sqlContext._    // Make its methods accessible.

    try {
      val lineRE = """^\s*([^|]+)\s*\|\s*([\d]+)\s*\|\s*([\d]+)\s*\|\s*(.*)~?\s*$""".r
      // Use flatMap to effectively remove bad lines.
      val verses = sc.textFile(argz.inpath) flatMap {
        case lineRE(book, chapter, verse, text) => 
          List(Verse(book, chapter.toInt, verse.toInt, text))
        case line => 
          Console.err.println("Unexpected line: $line")
          Nil  // Will be filtered out.
      }
      // The following invokes several "implicit" conversions and methods that we
      // imported through sqlContext._
      verses.registerAsTable("bible")

      verses.cache

      val godVerses = sql("SELECT * FROM bible WHERE text LIKE '%God%';")    
      
      val now = Timestamp.now()
      val out = s"${argz.outpath}/$now/gods"
      println(s"Writing verses that reference 'god' to: $out")
      godVerses.saveAsTextFile(out)
      println("Number of verses that mention God: "+godVerses.count())

      val counts = sql("""
        |SELECT * FROM (
        |  SELECT book, COUNT(*) FROM bible GROUP BY book) bc
        |WHERE bc.book <> '';
        """.stripMargin)
      // Collect all partitions into 1 partition. Otherwise, there are 100s
      // output from the last query!
      .coalesce(1)

      val out2 = s"${argz.outpath}/$now/book-counts"
      println(s"Writing book counts output to: $out2")
      counts.saveAsTextFile(out2)
    } finally {
      sc.stop()
    }

    // Exercise: Sort the output by the words. How much overhead does this add?

    // Exercise: For each output record, sort the list of (path, n) tuples by n.

    // Exercise: Try you own set of text files. First run Crawl5a to generate
    // the "web crawl" data.
  }
}
