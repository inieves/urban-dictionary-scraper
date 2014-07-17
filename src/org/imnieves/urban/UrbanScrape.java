package org.imnieves.urban;

import org.apache.commons.lang3.StringEscapeUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.util.List;
import java.util.LinkedList;

/**
 * The UrbanScrape class is designed to scrape words from the Urban Dictionary website at http://www.urbandictionary.com
 *
 * The class pulls the words from browse pages, using URLs of the form: http://www.urbandictionary.com/browse.php?character=A&page=2
 * Below is an example of what the returned HTML looks like.  As you can see, the actual word entries are between anchor tags.
 * So we search for those anchor tags and extract the text in between.
 *
 * <pre>
 * {@code	
 * SOME_HTML
 * <div id='columnist'>
 * <ul>
 * <li class=""><a href="/define.php?term=Aarhus">Aarhus</a></li>
 * ...
 * <li class="popular"><a href="/define.php?term=aarping">aarping</a></li>
 * </ul>
 * </div>
 * SOME_HTML
 * }
 * </pre>
 * 
 * @author Ian Morris Nieves
 * @version 1.0, May 2014
 */
public class UrbanScrape {


    // Debug
    private static final Boolean PRINT_STATUS_INFO = false;
    // Urban Dictionary URL formation
    private static final String URBAN_DICTIONARY_BROWSE_URL_BASE                      = "http://www.urbandictionary.com/browse.php";
    private static final String URBAN_DICTIONARY_BROWSE_URL_CHARACTER_QUERY_PARAMETER = "character";
    private static final String URBAN_DICTIONARY_BROWSE_URL_PAGE_QUERY_PARAMETER      = "page";
   	private static final String URBAN_DICTIONARY_DEFINE_URL_BASE                      = "http://www.urbandictionary.com/define.php";
   	private static final String URBAN_DICTIONARY_DEFINE_URL_TERM_QUERY_PARAMETER      = "term";
    // Scraping boundary settings
    private static final int MAX_PAGES_PER_FIRST_LETTER_TO_SCRAPE = 5000;
    private static final Character FIRST_FIRST_LETTER_TO_SCRAPE = 'A';
    private static final Character LAST_FIRST_LETTER_TO_SCRAPE = 'Z';
    // markers used by Scraper internally
    private static final String URBAN_DICTIONARY_START_OF_WORD_LIST_TOKEN = "<div id='columnist'>";
    private static final String URBAN_DICTIONARY_END_OF_WORD_LIST_TOKEN   = "</div>";
    private static final String[] URBAN_DICTIONARY_WORD_LIST_EXTRANEOUS_TOKENS = {"<ul>", "</ul>"};
    private static final String URBAN_DICTIONARY_HTML_BEFORE_WORD = "\">";
    private static final String URBAN_DICTIONARY_HTML_AFTER_WORD = "</a>";


	/**
	 * UrbanScrape main entry point.  Use this to scrape Urban Dictionary via Internet,
	 * and to produce a text file of resulting words, newline terminated.
	 *
	 * @param args  a string array, containing just 1 string, the path of a text file to put the Urban Dictionary words into.
	 *              The file is created if it doesnt exist, and truncated and then overwritten if it already exists.
	 */
    public static void main(final String args[]){
    	Boolean getDefinitions = false;
		// validate input, just a file path supplied
		if(args.length != 1 && args.length != 2){
		    printInstructionsAndExit();
		}
		// check for optional arguments
		if(args.length == 2){
			if(args[2].equals("-gd")){
				getDefinitions = true;
			}
			else{
				printInstructionsAndExit();
			}
		}			
		// create a way to write to the file
		PrintWriter writer = null;
		try{
		    writer = new PrintWriter(args[0]);		    
			printStatusInfo("Opened file at: " + args[0]);
		}
		catch(FileNotFoundException e){
		    System.err.println("Error: Unable to create/open file for writing");
		    System.err.println(e.getMessage());
		    System.err.println("System terminated abnormally.");
		    System.exit(1);
		}
		// get Urban Dictionary words from Internet
		long startTime = System.currentTimeMillis();
		List<String> urbanDictionaryWords = getUrbanDictionaryWordsCompleteListFromInternet(getDefinitions);
		long endTime = System.currentTimeMillis();
		// write Urban Dictionary words to file
		for(String word : urbanDictionaryWords)
		    writer.println(word);
		// flush output to file
		writer.close();		
	    printStatusInfo("Closed file at: " + args[0]);
	    printStatusInfo("Total Clock Time: " + getReadableTimeFromMillis(endTime-startTime));
	    printStatusInfo("Total Words: " + urbanDictionaryWords.size());
    }


    // Return a complete list of Urban Dictionary words, by incrementally scraping, one alphabet letter at a time, one page at a time for each letter.
    private static List<String> getUrbanDictionaryWordsCompleteListFromInternet(Boolean getDefinitions){
		printStatusInfo("Starting Urban Dictionary Scrape");
		List<String> urbanDictionaryWordsCompleteList = new LinkedList<String>();
		// iterate through all alphabet letters
		for(char firstLetterOfDictionaryWord = FIRST_FIRST_LETTER_TO_SCRAPE; firstLetterOfDictionaryWord <= LAST_FIRST_LETTER_TO_SCRAPE; firstLetterOfDictionaryWord++){
			printStatusInfo("Starting Scraping letter: " + firstLetterOfDictionaryWord);
		    int scrapedPageCount = 0;
		    int failedPageCount = 0;
		    int wordsAddedCount = 0;
		    long startTime = System.currentTimeMillis();
		    // iterate through all potential pages for a given alphabet letter (up to some max)
		    for(int pageNumberToScrape = 1; pageNumberToScrape <= MAX_PAGES_PER_FIRST_LETTER_TO_SCRAPE; pageNumberToScrape++){
				try{
					// get the words
				    List<String> urbanDictionaryWordsPartialList = getUrbanDictionaryWordsPartialListFromInternet(firstLetterOfDictionaryWord, pageNumberToScrape);
				    // get the definitions
				    if(getDefinitions)
					    urbanDictionaryWordsPartialList = appendUrbanDictionaryDefinitionsToWordsPartialList(urbanDictionaryWordsPartialList);
   				    // add each non-empty partial word list to the master word list
				    if(urbanDictionaryWordsPartialList.size() > 0){
						urbanDictionaryWordsCompleteList.addAll(urbanDictionaryWordsPartialList);
						scrapedPageCount++;
						wordsAddedCount += urbanDictionaryWordsPartialList.size();
						System.out.print(".");
				    }
				   	// don't add empty word lists, they indicate the last page of words for a given letter has been reached
				    else{
						System.out.print("x");
						break;
				    }
				}
				// if any failure occurred, continue gracefully trying subsequent pages and letters.  no need to completely fail just yet.
				catch(Exception e){
				    System.err.println("\nError: Unable to scrape letter: " + firstLetterOfDictionaryWord + " page: " + pageNumberToScrape);
				    System.err.println(e.getMessage());
				    e.printStackTrace();
				    System.err.println("Continuing to other pages.");
				    failedPageCount++;
				}
		    }
		    long endTime = System.currentTimeMillis();
		    printStatusInfo("\nEnding Scraping letter: " + firstLetterOfDictionaryWord
						   + " (scraped pages:" + scrapedPageCount + "  failed pages:" + failedPageCount 
						   + "  words:" + wordsAddedCount + "  clock time:" + getReadableTimeFromMillis(endTime-startTime)  + ")");
		}
		printStatusInfo("Ending Urban Disctionary Scrape");
		return urbanDictionaryWordsCompleteList;
    }


    // Return the list of Urban Dictionary words for a particular alphabet letter at a particular page number.
    // If the list requested does not exist or cannot be fetched for some reason, an empty list will be returned.
    // The letter must be capitalized and the number must be non-negative.
    private static List<String> getUrbanDictionaryWordsPartialListFromInternet(final char firstLetterOfDictionaryWord, final int pageNumberToScrape) throws IndexOutOfBoundsException, MalformedURLException, IOException{
		List<String> urbanDictionaryWordsPartialList = new LinkedList<String>();
		// create a BufferedReader to get the HTML data from Urban Dictionary
		URL urbanDictionaryBrowseURL = getUrbanDictionaryBrowseURL(firstLetterOfDictionaryWord, pageNumberToScrape);
		URLConnection urbanDictionaryBrowseURLConnection = urbanDictionaryBrowseURL.openConnection();
		BufferedReader urbanDictionaryBufferedReader = new BufferedReader(new InputStreamReader(urbanDictionaryBrowseURLConnection.getInputStream()));
		// iterate through each line of the HTML page that is returned
		boolean foundStartOfWordList = false;
		String line;
		while((line = urbanDictionaryBufferedReader.readLine()) != null){
		    // search for the start of the word list
		    if(!foundStartOfWordList && line.equals(URBAN_DICTIONARY_START_OF_WORD_LIST_TOKEN)){
			    foundStartOfWordList = true;
			}
			// or search for the end of the word list if we have already found the start of the word list
			else if(foundStartOfWordList && line.equals(URBAN_DICTIONARY_END_OF_WORD_LIST_TOKEN)){
				break;
			}
			// or skip past extraneous tokens if the we have already found the start of the word list
			else if(foundStartOfWordList && isExtraneousToken(line)){
				continue;
			}
			// word list entries found encoded in HTML, strip out the unnecessary parts and add to list,if we have already for the start of the word list
			else if(foundStartOfWordList){
			    String urbanDictionaryWord = getUrbanDictionaryWordFromHTMLLine(line);
			    urbanDictionaryWordsPartialList.add(urbanDictionaryWord);
			}
		}
		// release resources
		urbanDictionaryBufferedReader.close();
		return urbanDictionaryWordsPartialList;
    }


    // Return the list of Urban Discionary words, with the word definitions appended.
    // Each definition starts with a triple colon, and the full set ends with a newline.
	private static List<String> appendUrbanDictionaryDefinitionsToWordsPartialList(final List<String> partialWordList){
		List<String> newUrbanDictionaryWordsPartialList = new LinkedList<String>();
		for(String word : partialWordList){
			List<String> wordDefinitions = getUrbanDictionaryDefinitionsForWord(word);
			StringBuilder wordWithDefinitionsAppended = new StringBuilder(word);
			for(String definition : wordDefinitions){
				wordWithDefinitionsAppended.append(":::");
				wordWithDefinitionsAppended.append(definition);				
			}
			newUrbanDictionaryWordsPartialList.add(wordWithDefinitionsAppended.toString());
		}
		return newUrbanDictionaryWordsPartialList;
	}


	// Return a list of Urban Dictionary definitions for a given word
	private static List<String> getUrbanDictionaryDefinitionsForWord(final String word){
		List<String> urbanDictionaryDefinitionsForWord = new LinkedList<String>();
		// create a BufferedReader to get the HTML data from Urban Dictionary
		URL urbanDictionaryTermURL = getUrbanDictionaryTermURL(word);
		URLConnection urbanDictionaryTermURLConnection = urbanDictionaryTermURL.openConnection();
		BufferedReader urbanDictionaryBufferedReader = new BufferedReader(new InputStreamReader(urbanDictionaryTermURLConnection.getInputStream()));
		// iterate through each line of the HTML page that is returned
		boolean foundStartOfDefinitionList = false;
		String line;
		while((line = urbanDictionaryBufferedReader.readLine()) != null){
		    // search for the start of the word list
		    if(!foundStartOfWordList && line.equals(URBAN_DICTIONARY_START_OF_WORD_LIST_TOKEN)){
			    foundStartOfWordList = true;
			}
			// or search for the end of the word list if we have already found the start of the word list
			else if(foundStartOfWordList && line.equals(URBAN_DICTIONARY_END_OF_WORD_LIST_TOKEN)){
				break;
			}
			// or skip past extraneous tokens if the we have already found the start of the word list
			else if(foundStartOfWordList && isExtraneousToken(line)){
				continue;
			}
			// word list entries found encoded in HTML, strip out the unnecessary parts and add to list,if we have already for the start of the word list
			else if(foundStartOfWordList){
			    String urbanDictionaryWord = getUrbanDictionaryWordFromHTMLLine(line);
			    urbanDictionaryWordsPartialList.add(urbanDictionaryWord);
			}
		}
		// release resources
		urbanDictionaryBufferedReader.close();
		return urbanDictionaryDefinitionsForWord;
	}


    // Generate a valid Urban Dictionary browse url based on a particular alphabet letter and page number.
    // The letter must be capitalized and the number must be non-negative.
    private static URL getUrbanDictionaryBrowseURL(final char firstLetterOfDictionaryWord, final int dictionaryPageNumber) throws MalformedURLException{
		// input validation
		if(firstLetterOfDictionaryWord < 'A'
		    || firstLetterOfDictionaryWord > 'Z'){
		    throw new MalformedURLException("Cannot get Urban Dictionary Browse URL for first letter: " + firstLetterOfDictionaryWord);
		}
		else if(dictionaryPageNumber < 0){
		    throw new MalformedURLException("Cannot get Urban Dictionary Browse URL for page number: " + dictionaryPageNumber);
		}
		// generate a valid URL
		URL urbanDictionaryBrowseURL = null;
		try{
		    urbanDictionaryBrowseURL = new URL(URBAN_DICTIONARY_BROWSE_URL_BASE
							   + "?" + URBAN_DICTIONARY_BROWSE_URL_CHARACTER_QUERY_PARAMETER + "=" + firstLetterOfDictionaryWord
							   + "&" + URBAN_DICTIONARY_BROWSE_URL_PAGE_QUERY_PARAMETER + "=" + dictionaryPageNumber);
		}
		catch(MalformedURLException mue){
		    throw mue;
		}
		// return valid URL
		return urbanDictionaryBrowseURL;
    }


    // Generate a valid Urban Dictionary term url based on a particular Urban Dictionary word.
    private static URL getUrbanDictionaryTermURL(final String term) throws MalformedURLException{
		// input validation
		if(term == null){
		    throw new MalformedURLException("Cannot get Urban Dictionary Term URL for a numm term.");
		}
		// prepare term for sending as an HTML URL
		term = StringEscapeUtils.escapeHtml4(term);
		// generate a valid URL
		URL urbanDictionaryTermURL = null;
		try{
		    urbanDictionaryTermURL = new URL(URBAN_DICTIONARY_TERM_URL_BASE
							   + "?" + URBAN_DICTIONARY_DEFINE_URL_TERM_QUERY_PARAMETER + "=" + term);
		}
		catch(MalformedURLException mue){
		    throw mue;
		}
		// return valid URL
		return urbanDictionaryTermURL;
    }
    

    // Return an actual Urban Dictionary word, with no special HTML characters, given a raw html word entry on a page
    private static String getUrbanDictionaryWordFromHTMLLine(final String html) throws IndexOutOfBoundsException{
		// get the raw word from HTML, including encoding
		int indexOfHTMLBeforeWord = html.lastIndexOf(URBAN_DICTIONARY_HTML_BEFORE_WORD);
		int lengthOfHTMLBeforeWord = URBAN_DICTIONARY_HTML_BEFORE_WORD.length();
		int indexOfHTMLAfterWord = html.indexOf(URBAN_DICTIONARY_HTML_AFTER_WORD);
		String rawWord = html.substring(indexOfHTMLBeforeWord + lengthOfHTMLBeforeWord, indexOfHTMLAfterWord);
		// process the raw word, to unescape HTML
		String processedWord = StringEscapeUtils.unescapeHtml4(rawWord);
		// return processed word
		return processedWord;
    }


    // Return true iff the supplied token is found in the Urban Dictionary word list but is not actually a parseable line
    private static Boolean isExtraneousToken(final String token){
		for(String extraneousToken : URBAN_DICTIONARY_WORD_LIST_EXTRANEOUS_TOKENS){
		    if(extraneousToken.equals(token))
				return true;
		}
		return false;
    }


    // Return a human readable string representation of time
    private static String getReadableTimeFromMillis(long millis){
		long seconds = (millis / 1000) % 60;
		long minutes = ((millis / (1000*60)) % 60);
		long hours   = (millis / (1000*60*60));
		return hours + "h:" + minutes + "m:" + seconds + "s";
    }


    // Output some simple program usage instructions to the user, and exit the program.
	private static void printInstructionsAndExit(){
		System.out.println("Please supply one required argument: 1) the path of a file to create (potentially truncate first) and write text into.");
		System.out.println("You may also supply an optional second argument: 2) -gd to get definitions of the words");
		System.exit(1);
	}


    // Print the supplied message to the System.out print stream iff PRINT_STATUS_INFO is true
    private static void printStatusInfo(final String message){
    	if(PRINT_STATUS_INFO)
		    System.out.println(message);
    }

}