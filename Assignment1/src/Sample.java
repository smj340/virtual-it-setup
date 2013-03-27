import java.io.IOException;
import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;


public class Sample {

	static AmazonEC2 ec2;
	public static void main(String[] args) {
		AWSCredentials credentials = null;
		try {
			credentials = new PropertiesCredentials(
					CreateInitialVMs.class.getResourceAsStream("AwsCredentials.properties"));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

         /*********************************************
          * 
          *  #1 Create Amazon Client object
          *  
          *********************************************/
    	 System.out.println("#1 Create Amazon Client object");
         ec2 = new AmazonEC2Client(credentials);
         
         try {
     		System.out.println("Restoring an Instance from AMI : ami-04f44a6d");
         	int minInstanceCount = 1; // create 1 instance
         	int maxInstanceCount = 1;
         	RunInstancesRequest rir = new RunInstancesRequest("ami-04f44a6d", minInstanceCount, maxInstanceCount);

         	rir.setKeyName("testElastic");
         	rir.withSecurityGroups("Prachi");
         	Placement p = new Placement();
            p.setAvailabilityZone("us-east-1a");
            rir.setPlacement(p);
         	
         	RunInstancesResult result = ec2.runInstances(rir);
         	
     	} catch (AmazonServiceException ase) {
     	System.out.println("Caught Exception: " + ase.getMessage());
     	System.out.println("Reponse Status Code: " + ase.getStatusCode());
     	System.out.println("Error Code: " + ase.getErrorCode());
     	System.out.println("Request ID: " + ase.getRequestId());
     	}
	}

}
