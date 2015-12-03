import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * A product and all of its reviews
 *
 * @author Feng Mai
 */


public class Item {

    public String itemID;
    public String itemName;
    public double price;
    public int reveiwsCount;
    public ArrayList<Review> reviews;

    public Item(String theitemid) {
        itemID = theitemid;
        reviews = new ArrayList<Review>();
    }

    public void addReview(Review thereview) {
        reviews.add(thereview);
    }

    /**
     * Fetch all reviews for the item from Amazon.com
     * <p>
     * Modified by
     *
     * @author Prem
     */
    public void fetchReview() throws InterruptedException {
        int limit = 100; //Number of pages from which reviews to be retrieved
        int retry = 0;
        String url = "http://www.amazon.com/product-reviews/" + itemID
                + "/?showViewpoints=0&sortBy=byRankDescending&pageNumber=" + 1;

        // Get the max number of review pages;
        org.jsoup.nodes.Document reviewpage1 = null;
        retry = 0;
        while (true) {
            try {
                reviewpage1 = Jsoup.connect(url).timeout(10 * 1000).get();
                break;
            } catch (IOException e) {
                if (retry < 3) {
                    retry++;
                    Thread.sleep(60000);
                    System.out.println(retry + "-Retry after 1 minute. Waiting... for maxpage");
                } else {
                    e.printStackTrace();
                    break;
                }
            }
        }
        int maxpage = 1;
        Elements pagelinks = reviewpage1.select("a[href*=pageNumber=]");
        if (pagelinks.size() != 0) {
            ArrayList<Integer> pagenum = new ArrayList<Integer>();
            for (Element link : pagelinks) {
                try {
                    pagenum.add(Integer.parseInt(link.text().replace(",", "")));
                } catch (NumberFormatException nfe) {
                }
            }
            //Taking only first limit*10 reviews if there are more than that
            maxpage = Collections.max(pagenum) > limit ? limit : Collections.max(pagenum);
        }

        // collect review from each of the review pages;
        for (int p = 1; p <= maxpage; p = p + 1) {
            url = "http://www.amazon.com/product-reviews/"
                    + itemID
                    + "/?showViewpoints=0&sortBy=byRankDescending&pageNumber="
                    + p;
            org.jsoup.nodes.Document reviewpage = null;
            retry = 0;
            while (true) {
                try {
                    reviewpage = Jsoup.connect(url).timeout(10 * 1000).get();
                    if (reviewpage.select("div.a-section.review").isEmpty()) {
                        System.out.println(itemID + " " + "no reivew");
                    } else {
                        Elements reviewsHTML = reviewpage.select("div.a-section.review");
                        Review review;
                        for (Element reviewHTML : reviewsHTML) {
                            review = this.cleanReviewBlock(reviewHTML);
                            this.addReview(review);
                        }
                    }
                    break;
                } catch (IOException e) {
                    if (retry < 3) {
                        retry++;
                        Thread.sleep(60000);
                        System.out.println(retry + "-Retry after 1 minute. Waiting... for page = "+p);
                    } else {
                        e.printStackTrace();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Fetch the product/item details such as item name, item price.
     *
     * @author Prem
     */
    public void fetchInfo() {
        String url = "http://www.amazon.com/gp/aw/s/?k=" + this.itemID;
        try {

            org.jsoup.nodes.Document productPage = null;
            productPage = Jsoup.connect(url).timeout(10 * 1000).get();
            Element productBlock = productPage.select("form").get(1);
            if (productBlock != null) {
                this.itemName = productBlock.select("a").get(0).text();
                String price = productPage.select("form").get(1).select("b").first().text().substring(1);
                this.price = Double.parseDouble(price.replace(",",""));
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(itemID + " " + "Exception" + " " + e.getClass());
        }
    }

    /**
     * [FOR NEW UPDATED PAGE LAYOUT]Clean the HTML block that contains the reivew
     * Note: The other method with this name is for older page layout
     *
     * @param reviewHTML An HTML Element that contains a single reveiw
     * @return
     * @author Prem
     */
    public Review cleanReviewBlock(Element reviewHTML) {

        String reviewID = reviewHTML.id();
        int totalVotes = 0, posVotes = 0;
        int rating = 0;
        String reviewTitle = reviewHTML.select("div.a-row").get(1).select("a.review-title").text();
        String reviewText = reviewHTML.select("div.a-row.review-data > span.a-size-base.review-text").text();
        String helpfulVotes = reviewHTML.select("div.a-row.helpful-votes-count").text();
        String stars = reviewHTML.select("div.a-row").get(1).select("span.a-icon-alt").text();

        //Get vote counts for each review
        helpfulVotes = helpfulVotes.replace(",", "");
        if (helpfulVotes.length() != 0) {
            posVotes = Integer.parseInt(helpfulVotes.split(" ")[0]);
            totalVotes = Integer.parseInt(helpfulVotes.split(" ")[2]);
        }

        //Get ratings
        Pattern pattern = Pattern.compile("[\\d]");
        Matcher matcher = pattern.matcher(stars);
        if (matcher.find()) {
            rating = Integer.parseInt(matcher.group(0));
        }

        Review thereview = new Review(this.itemID, reviewID, "", "", reviewTitle, rating, 5, posVotes,
                totalVotes, false, false, new Date(), reviewText);
        return thereview;

    }


    /**
     * Write reviews of a product to its corresponding file named by its itemID
     *
     * @param file Input file to which the reviews will be written
     * @author Prem
     */
    public void writeReviewsToFile(File file) {

        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(file, true));
            for (Review review : this.reviews) {
                bw.write(review.reviewID + "\t" + review.rating + "\t" + review.title + "\t"
                        + review.helpfulVotes + "\t" + review.totalVotes + "\t" + review.content + "\n");
            }
            bw.flush();
            bw.close();
            System.out.println(new DateTime() + " " + this.itemID + " Reviews write complete!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write itemInfo of a product to itemsInfo file
     *
     * @param file
     * @author Prem
     */
    public void writeItemInfoToFile(File file) {

        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(file, true));
            bw.write(this.itemID + "\t" + this.itemName + "\t" + this.price + "\n");
            bw.flush();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * cleans the html block that contains a review
     *
     * @param reviewBlock a html review block
     * @return
     * @throws ParseException
     */
    public Review cleanReviewBlock(String reviewBlock) throws ParseException {
        org.jsoup.nodes.Document doc = Jsoup.parse(reviewBlock);
        String theitemID = this.itemID;
        String reviewID = "";
        String customerName = "";
        String customerID = "";
        String title = "";
        int rating = 0;
        int fullRating = 5;
        int helpfulVotes = 0;
        int totalVotes = 0;
        boolean verifiedPurchase = false;
        boolean realName = false;

        String content = "";

        // review id
        Elements reviewIDs = doc.getElementsByAttribute("name");
        reviewID = reviewIDs.first().attr("name");

        // customer name and id
        Elements customerIDs = doc.getElementsByAttributeValueContaining(
                "href", "/gp/pdp/profile/");
        if (customerIDs.size() > 0) {
            Element customer = customerIDs.first();
            String customerhref = customer.attr("href");
            String patternString = "(/gp/pdp/profile/)(.+)";
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(customerhref);
            matcher.find();
            // cutomer id;
            customerID = matcher.group(2);
            // customer name;
            customerName = customer.text();
        }
        // title
        Elements reviewTitles = doc.getElementsByAttributeValue("style",
                "vertical-align:middle;");
        title = reviewTitles.first().getElementsByTag("b").text();

        // rating
        Elements stars = doc.getElementsByClass("swSprite");
        String starinfo = stars.first().text();
        rating = Integer.parseInt(starinfo.substring(0, 1));

        // usefulness voting
        Elements votings = doc
                .getElementsContainingOwnText("people found the following review helpful");
        if (votings.size() > 0) {
            String votingtext = votings.first().text();
            Pattern pattern2 = Pattern.compile("(\\S+)( of )(\\S+)");
            Matcher matcher2 = pattern2.matcher(votingtext);
            matcher2.find();
            // customer id;
            helpfulVotes = Integer.parseInt(matcher2.group(1).replaceAll(",", ""));
            totalVotes = Integer.parseInt(matcher2.group(3).replaceAll(",", ""));
        }

        // verified purchase and real name
        Elements verified = doc.getElementsByClass("crVerifiedStripe");
        if (verified.size() > 0)
            verifiedPurchase = true;
        Elements realname = doc.getElementsByClass("s_BadgeRealName");
        if (realname.size() > 0)
            realName = true;

        // review date
        Elements date = doc.getElementsByTag("nobr");
        String datetext = date.first().text();
        Date reviewDate = new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
                .parse(datetext);

        // review content
        Element contentDoc = doc.getElementsByClass("reviewText").first();
        content = contentDoc.text();
        Review thereview = new Review(theitemID, reviewID, customerName,
                customerID, title, rating, fullRating, helpfulVotes,
                totalVotes, verifiedPurchase, realName, reviewDate, content);
        return thereview;
    }

    /**
     * Write all reviews into a Sqlite database
     *
     * @param database Sqlite database file path
     * @param API      a boolean value indicating whether to get item related
     *                 information from Product Advertising API (e.g. price, sells
     *                 rank)
     * @throws InvalidKeyException
     * @throws ClassNotFoundException
     * @throws NoSuchAlgorithmException
     * @throws ClientProtocolException
     * @throws SQLException
     * @throws IOException
     */
    public synchronized void writeReviewsToDatabase(String database, boolean API)
            throws InvalidKeyException, ClassNotFoundException,
            NoSuchAlgorithmException, ClientProtocolException, SQLException,
            IOException {
        if (API == true) {
            DatabaseUpdater.doUpdate(database, reviews, itemID,
                    getXMLLargeResponse());
        } else {
            DatabaseUpdater.doUpdate(database, reviews, itemID, "");
        }
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        String timenow = dateFormat.format(date);
        System.out.println(this.itemID + " Finished " + timenow);

    }

    /**
     * @return the RAW XML document of ItemLookup (Large Response) from Amazon
     * product advertisement API
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     * @throws ClientProtocolException
     * @throws IOException
     */
    public String getXMLLargeResponse() throws InvalidKeyException,
            NoSuchAlgorithmException, ClientProtocolException, IOException {
        String responseBody = "";
        String signedurl = signInput();
        try {
            HttpClient httpclient = new DefaultHttpClient();
            HttpGet httpget = new HttpGet(signedurl);
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            responseBody = httpclient.execute(httpget, responseHandler);
            // responseBody now contains the contents of the page
            // System.out.println(responseBody);
            httpclient.getConnectionManager().shutdown();
        } catch (Exception e) {
            System.out.println("Exception" + " " + itemID + " " + e.getClass());
        }
        return responseBody;
    }

    /**
     * Sign the REST request
     *
     * @return REST request to acquire a "Large ResponseGroup" from ItemLookup
     * operation in Amazon Advertising API
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    private String signInput() throws InvalidKeyException,
            NoSuchAlgorithmException, UnsupportedEncodingException {
        // Input to Sign;
        Map<String, String> variablemap = new HashMap<String, String>();
        //*****ADD YOUR AssociateTag HERE*****
        variablemap.put("AssociateTag", "");
        variablemap.put("Operation", "ItemLookup");
        variablemap.put("Service", "AWSECommerceService");
        variablemap.put("ItemId", itemID);
        variablemap.put("ResponseGroup", "Large");

        // Sign and get the REST url;
        SignedRequestsHelper helper = new SignedRequestsHelper();
        String signedurl = helper.sign(variablemap);
        return signedurl;
    }

    /**
     * Get and print item info using Amazon's Product Advertising API. NOT
     * COMPLETE
     *
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public void getBookSaleInfo() throws InvalidKeyException,
            NoSuchAlgorithmException, UnsupportedEncodingException {
        String signedurl = signInput();
        System.out.println(signedurl);

        // Info requested;
        ArrayList<String> TagNames = new ArrayList<String>();
        TagNames.add("Title");
        TagNames.add("SalesRank");
        TagNames.add("ListPrice");
        TagNames.add("LowestNewPrice");
        TagNames.add("LowestUsedPrice");
        TagNames.add("TotalNew");
        TagNames.add("TotalUsed");
        TagNames.add("PublicationDate");
        TagNames.add("Author");
        TagNames.add("Publisher");
        TagNames.add("EditorialReview");
        // fetch info and print;
        Map<String, String> InfoTagMap = fetchInfo(signedurl, TagNames);
        System.out.println(InfoTagMap.toString());
    }

    /**
     * Fetch the results of product info requested and return a Hashmap
     *
     * @param requestUrl Signed REST request url
     * @param TagNames   Strings ArrayList of product info tags
     *                   (http://docs.amazonwebservices
     *                   .com/AWSECommerceService/latest/DG/RG_Large.html)
     * @return Map(Tag Name, Value)
     */
    private static Map<String, String> fetchInfo(String requestUrl,
                                                 ArrayList<String> TagNames) {
        Map<String, String> InfoTagMap = new HashMap<String, String>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(requestUrl);
            if (doc.getElementsByTagName("IsValid").item(0).getTextContent()
                    .equals("True")) {
                for (String tag : TagNames) {
                    NodeList titleNode = doc.getElementsByTagName(tag);
                    if (tag.equals("Title")) {
                        InfoTagMap.put(tag, titleNode.item(0).getTextContent());
                    } else {
                        ArrayList<String> infolist = new ArrayList<String>();
                        for (int i = 0; i < titleNode.getLength(); i++) {
                            infolist.add(titleNode.item(i).getTextContent());
                        }
                        InfoTagMap.put(tag, infolist.toString());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return InfoTagMap;
    }

}
