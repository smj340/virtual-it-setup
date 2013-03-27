
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AllocateAddressResult;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateImageRequest;
import com.amazonaws.services.ec2.model.CreateImageResult;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairResult;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DetachVolumeRequest;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;


public class CreateInitialVMs {

    static AmazonEC2 ec2;
    static List<VMNode> nodes = new ArrayList<VMNode>();

    public static void main(String[] args) throws Exception {
    	start();
    }
    
    public static void start() {
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
            
            /*********************************************
             * 
             *  #6 Create an Instance
             *  
             *********************************************/
            System.out.println("#5 Create an Instance");
            
            String imageId = "ami-76f0061f"; //Basic 32-bit Amazon Linux AMI
            int minInstanceCount = 1; 
            int maxInstanceCount = 2; //create 2 instances
            String keyPairName = "testElastic";
            String securityGroupName = "Prachi";
            ArrayList<String> securityGroup = new ArrayList<String>();
            securityGroup.add(securityGroupName);
            
            RunInstancesRequest rir = new RunInstancesRequest(imageId, minInstanceCount, maxInstanceCount);
            rir.setKeyName(keyPairName);
            rir.setSecurityGroups(securityGroup);
            Placement p = new Placement();
            p.setAvailabilityZone("us-east-1a");
            rir.setPlacement(p);
            
            RunInstancesResult result = ec2.runInstances(rir);
            
          //get instanceId from the result
            List<Instance> resultInstance = result.getReservation().getInstances();
            String createdInstanceId = null;
            for (Instance ins : resultInstance){
            	createdInstanceId = ins.getInstanceId();
            	
            	System.out.println("State of instance " + ins.getInstanceId() + " : ");
            	while (true)
                {
                    try {
    					Thread.sleep(1000);
    				} catch (InterruptedException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}
                    System.out.print(ins.getState().getName()+"\n");
                    
                    ins = updatedInstance(ins);
                    //if(ins.getPublicIpAddress()!= null){
                    //if(ins.getState().getName().equals("running")){
                    if(ins.getPublicIpAddress()!= null && ins.getState().getName().equals("running")){
                        break;
                    }

                }
            	
            	System.out.println("State: " + ins.getState().getName());
            	
            	System.out.println("New instance has been created: "+ins.getInstanceId());
            	System.out.println("Instance Key: " + ins.getKeyName());
            	System.out.println("Public DNS Name: " + ins.getPublicDnsName());
            	System.out.println("Public DNS IP Address: " + ins.getPublicIpAddress());
            	System.out.println("Instance ID: " + ins.getInstanceId());
            	
            	VMNode vmn = new VMNode();
            	
            	vmn.setInstanceId(ins.getInstanceId());
            	
            	/*********************************************
    			* Allocate elastic IP addresses.
    			*********************************************/
                System.out.println("Public IP before association: " + ins.getPublicIpAddress());
                
                ins = allocateElasticIP(ins);
    			
                vmn.setElasticIp(ins.getPublicIpAddress());
                
    			System.out.println("Public IP after association: " + ins.getPublicIpAddress());
    			
    			
    			/*********************************************
            	* Create a volume
            	*********************************************/
            	
            	String createdVolumeId = createVolume();
            	
            	/*********************************************
                 * Attach the volume to the instance
                 *********************************************/
            	
            	attachVolume(createdInstanceId, createdVolumeId);
            	
            	vmn.setVolumeId(createdVolumeId);
            	
            	/*********************************************
                 * Detach the volume from the instance
                 *********************************************/
            	
            	detachVolume(createdInstanceId, createdVolumeId);
            	
            	/***********************************
                 * Create an AMI from an instance
                 *********************************/
            	
            	String createdImageId = createAmi(createdInstanceId); 
            	
            	vmn.setAmiId(createdImageId);
            	nodes.add(vmn);
            	
            	/***********************************
                 * Stop the instance
                 *********************************/
            	//stopInstance(ins.getInstanceId());
            	//Diassociate Elastic IP from instance
            	disassociateElasticIp(ins.getPublicIpAddress());
            	
            }
            
            System.out.println("Listing Nodes: ");
            for(VMNode n : nodes) {
            	System.out.println("Instance ID: " + n.getInstanceid());
            	System.out.println("Elastic IP: " + n.getElasticIp());
            	System.out.println("Volume ID: " + n.getVolumeId());
            	System.out.println("AMI ID: " + n.getAmiId());
            	System.out.println();
            }
            
            /*********************************************
             *  
             *  #10 shutdown client object
             *  
             *********************************************/
            ec2.shutdown();
            
            
            
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }
    
    //Gives fresh copy of instance
    public static Instance updatedInstance(Instance ins) {
    	
    	int i,j;
    	List<Reservation> reserveList = ec2.describeInstances().getReservations();

        for(i =0 ; i<reserveList.size();i++){
                List<Instance> instanceList = reserveList.get(i).getInstances();
                for(j = 0;j <instanceList.size();j++){
                        if(instanceList.get(j).getInstanceId().equalsIgnoreCase(ins.getInstanceId())){
                                return instanceList.get(j);
                        }
                }
        }
        return null;
    }
    
    //Allocates new elastic IP addresses.
    public static Instance allocateElasticIP(Instance ins) {
    	//allocate
		AllocateAddressResult elasticResult = ec2.allocateAddress();
		String elasticIp = elasticResult.getPublicIp();
		System.out.println("New elastic IP: "+elasticIp);
			
		//associate
		AssociateAddressRequest aar = new AssociateAddressRequest();
		aar.setInstanceId(ins.getInstanceId());
		aar.setPublicIp(elasticIp);
		ec2.associateAddress(aar);
		
		ins = updatedInstance(ins);
		
		return ins;
    }
    
    //Creates Volume
    public static String createVolume() {
    	System.out.println("Create a volume");
    	CreateVolumeRequest cvr = new CreateVolumeRequest();
        cvr.setAvailabilityZone("us-east-1a");
        cvr.setSize(10); //size = 10 gigabytes
    	CreateVolumeResult volumeResult = ec2.createVolume(cvr);
    	String createdVolumeId = volumeResult.getVolume().getVolumeId();
    	return createdVolumeId;
    }
    
    //Attaches volume to instance
    public static void attachVolume(String instanceId, String volumeId){
    	System.out.println("Attach the volume to the instance");
    	AttachVolumeRequest avr = new AttachVolumeRequest();
    	avr.setVolumeId(volumeId);
    	avr.setInstanceId(instanceId);
    	avr.setDevice("/dev/sdf");
    	ec2.attachVolume(avr);
    	System.out.println("Volume attached");
    	try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    //Detaches volume from instance
    public static void detachVolume(String instanceId, String volumeId) {
    	DetachVolumeRequest dvr = new DetachVolumeRequest();
    	dvr.setVolumeId(volumeId);
    	dvr.setInstanceId(instanceId);
    	ec2.detachVolume(dvr);
    	
    	System.out.println("Volume Detached");
    }
    
    //Creates AMI of instance 
    public static String createAmi(String instanceId) {
    	CreateImageRequest cir = new CreateImageRequest();
		cir.setInstanceId(instanceId);
		cir.setName("AMI-" + instanceId);
		CreateImageResult createImageResult = ec2.createImage(cir);
		String createdImageId = createImageResult.getImageId();
    	
		return createdImageId;
    }
    
    //Teminates the instance
    public static void terminateInstance(String instanceId) {
		List<String> terminateInstances = new LinkedList<String>();
		terminateInstances.add(instanceId);
		TerminateInstancesRequest tir = new TerminateInstancesRequest(terminateInstances);
		ec2.terminateInstances(tir);
		
		System.out.println("Instance " + instanceId + " terminated");
	}
    
  //Stops the instance
    public static void stopInstance(String instanceId) {
		List<String> stopInstances = new LinkedList<String>();
		stopInstances.add(instanceId);
		StopInstancesRequest sir = new StopInstancesRequest(stopInstances);
		ec2.stopInstances(sir);
		
		System.out.println("Instance : " + instanceId + " stoped");
	}
    
  //Disassociate Elastic IP from instance
    public static void disassociateElasticIp(String elasticIp) {
    	System.out.println("Diassociating elastic ip : " + elasticIp);
		DisassociateAddressRequest dar = new DisassociateAddressRequest();
		dar.setPublicIp(elasticIp);
		ec2.disassociateAddress(dar);
    }
}