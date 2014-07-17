urban-dictionary-scraper
========================

Overview:
This Java source can help you scrape (hilarious) words from Urban Dictionary.
It is relatively fast, scraping the full 16 Mb data set (1.3 M words) in about an hour on a fast internet connection.
Get ready for a good laugh!

Notes:
You will need org.apache.commons.lang3.StringEscapeUtils to compile the source here.
For your convenience, I have included a version of that in the lib directory, but I advise you to get the latest at:
http://commons.apache.org/proper/commons-lang/download_lang.cgi

Directories:
/src : contains the source code to the scraper I wrote
/lib : contains the Apache Commons library you will need to compile my source
/dict : a scrape of Urban Dictionary showing what the output is typically like (hilarious)