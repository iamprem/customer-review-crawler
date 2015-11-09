import java.io.File;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import java.sql.SQLException;
import java.text.ParseException;
import java.io.IOException;
import java.util.Set;

public class crawler {

	/**
	 * @param args
	 * @throws IOException
	 * @throws ParseException
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException, ParseException,
			ClassNotFoundException, SQLException, InvalidKeyException,
			NoSuchAlgorithmException, InterruptedException {

		GetASINbyNode asins = new GetASINbyNode("541966%2C1232597011",1,3);
        asins.getIDList();
        Set<String> productIds = asins.items.returnIDsAsSet();

        for (String productId : productIds){
            Item product = new Item(productId);
            product.fetchInfo();
            product.fetchReview();

            //Write fetched info and reviews to files
            File reviewFile = new File(System.getProperty("user.home")+"/Desktop/Reviews/"+product.itemID+".txt");
            File itemInfoFile = new File(System.getProperty("user.home")+"/Desktop/ItemInfo/itemInfo.txt");
            itemInfoFile.getParentFile().mkdir();
            itemInfoFile.createNewFile();
            reviewFile.getParentFile().mkdir();
            reviewFile.createNewFile();
            product.writeItemInfoToFile(itemInfoFile);
            product.writeReviewsToFile(reviewFile);
        }

	}

}
