import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.Timer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;

public class MonitorVMs {
	
	static AmazonEC2 ec2;
	public static int timerCounter = 5;
	static List<VMNode> nodes;
	
	public static void main(String[] args) {
		
		Scanner in = new Scanner(System.in);
		
		CreateInitialVMs.start();
		
		AWSCredentials credentials = null;
		
		nodes = CreateInitialVMs.nodes;
		
		System.out.println("Listing Nodes in MonitorVMs: ");
        for(VMNode n : nodes) {
        	System.out.println("Instance ID: " + n.getInstanceid());
        	System.out.println("Elastic IP: " + n.getElasticIp());
        	System.out.println("Volume ID: " + n.getVolumeId());
        	System.out.println("AMI ID: " + n.getAmiId());
        	System.out.println();
        }
        
        long time1 = System.currentTimeMillis();
        
        String decision = "n";
        
        do {
        	System.out.println("Start the CloudWatch? : (y/n)");
            decision = in.next();
        }while(decision.equals("N") || decision.equals("n"));
        
        long time2 = System.currentTimeMillis();
        
        System.out.println("Time Difference : " + (time2 - time1));
        
        //CloudWatch Started
		try {
			credentials = new PropertiesCredentials(
					CreateInitialVMs.class.getResourceAsStream("AwsCredentials.properties"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		ec2 = new AmazonEC2Client(credentials);
		List<Instance> runningInstanceList = null;
		
		//create CloudWatch client
		AmazonCloudWatchClient cloudWatch = new AmazonCloudWatchClient(credentials) ;
		
		//create request message
		GetMetricStatisticsRequest statRequest = new GetMetricStatisticsRequest();
		
		//set up request message
		statRequest.setNamespace("AWS/EC2"); //namespace
		statRequest.setPeriod(60); //period of data
		ArrayList<String> stats = new ArrayList<String>();
		
		//Use one of these strings: Average, Maximum, Minimum, SampleCount, Sum 
		stats.add("Average"); 
		stats.add("Sum");
		statRequest.setStatistics(stats);
		
		//Use one of these strings: CPUUtilization, NetworkIn, NetworkOut, DiskReadBytes, DiskWriteBytes, DiskReadOperations  
		statRequest.setMetricName("CPUUtilization"); 
		
		// set time
		GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		calendar.add(GregorianCalendar.SECOND, -1 * calendar.get(GregorianCalendar.SECOND)); // 1 second ago
		Date endTime = calendar.getTime();
		calendar.add(GregorianCalendar.MINUTE, -10); // 10 minutes ago
		Date startTime = calendar.getTime();
		statRequest.setStartTime(startTime);
		statRequest.setEndTime(endTime);
		
		
		GetMetricStatisticsResult statResult;
		List<Datapoint> dataList;
		Double averageCPU;
		Date timeStamp;
		
		Timer t1 = new Timer();
		Timer t2 = new Timer();
		
		long delay1 = 5*60*1000;
		long delay2 = 9*60*1000;
		
		t1.schedule(new Task("Obj1"), 0, delay2);
		t2.schedule(new Task("Obj2"), delay1, delay2);
		
		while(true) {
			try {
				Thread.sleep(1000);
				//System.out.println("Timer: " + MonitorVMs.timerCounter);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(MonitorVMs.timerCounter == 9) {
				runningInstanceList = getInstanceList();
				for(Instance ins : runningInstanceList) {
					if(ins.getState().getName().equals("running")) {
						System.out.println("for Instance: " + ins.getInstanceId() + "  State: " + ins.getState());
						System.out.println("State is: " + ins.getState().getName());
						ArrayList<Dimension> dimensions = new ArrayList<Dimension>();
						dimensions.add(new Dimension().withName("InstanceId").withValue(ins.getInstanceId()));
						
						statRequest.setDimensions(dimensions);
						
						//get statistics
						statResult = cloudWatch.getMetricStatistics(statRequest);
						
						//display
						System.out.println(statResult.toString());
						dataList = statResult.getDatapoints();
						System.out.println("dataList: " + dataList.toString());
						averageCPU = null;
						timeStamp = null;
						for (Datapoint data : dataList){
							averageCPU = data.getAverage();
							timeStamp = data.getTimestamp();
							System.out.println("Average CPU utlilization for last 10 minutes: "+averageCPU);
							System.out.println("Totl CPU utlilization for last 10 minutes: "+data.getSum());
							if(averageCPU <= 20.00) {
								System.out.println("Calling saveSnapshot function and then terminating instance.");
								for(VMNode n : nodes) {
									if(n.getInstanceid().equals(ins.getInstanceId())) {//Delete this instance
										//Detach Volume
										String instanceId = n.getInstanceid();
										String volumeId = n.getVolumeId();
										MonitorVMs.detachVolume(instanceId, volumeId);
										//Create AMI from instance
										String imageId = MonitorVMs.createAmi(instanceId);
										n.setAmiId(imageId);
										//Terminate instance
										MonitorVMs.terminateInstance(instanceId);
									}
								}
							}
							else if(averageCPU >= 70.00) {
								System.out.println("Use Autoscaling");
							}
						}
					}
				}
			}
		} //end while
	}
	
	//Returns list of running instances
	public static List<Instance> getInstanceList() {
		int i,j;
    	List<Reservation> reserveList = ec2.describeInstances().getReservations();
    	List<Instance> runningInstanceList = new ArrayList<Instance>();

        for(i =0 ; i<reserveList.size();i++){
                List<Instance> instanceList = reserveList.get(i).getInstances();
                for(j = 0;j <instanceList.size();j++){
                	runningInstanceList.add((Instance) instanceList.get(j));
                	System.out.println("Instance : " +  instanceList.get(j).getInstanceId());
                }
        }
        return runningInstanceList;
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
    	System.out.println("Allocating new Elastic IP");
		AllocateAddressResult elasticResult = ec2.allocateAddress();
		String elasticIp = elasticResult.getPublicIp();
		System.out.println("New elastic IP: "+elasticIp);
		
		MonitorVMs.associateElasticIP(ins.getInstanceId(), elasticIp);
		
		ins = updatedInstance(ins);
		
		return ins;
    }
	
    public static void associateElasticIP(String instanceId, String elasticIp) {
		//associate
    	System.out.println("Associating Elastic IP : " + elasticIp + " to instance : "  + instanceId);
		AssociateAddressRequest aar = new AssociateAddressRequest();
		aar.setInstanceId(instanceId);
		aar.setPublicIp(elasticIp);
		ec2.associateAddress(aar);	
    }
    
    //Creates Volume
    public static String createVolume() {
    	System.out.println("Creating a volume");
    	CreateVolumeRequest cvr = new CreateVolumeRequest();
        cvr.setAvailabilityZone("us-east-1a");
        cvr.setSize(10); //size = 10 gigabytes
    	CreateVolumeResult volumeResult = ec2.createVolume(cvr);
    	String createdVolumeId = volumeResult.getVolume().getVolumeId();
    	return createdVolumeId;
    }
    
    //Attaches volume to instance
    public static void attachVolume(String instanceId, String volumeId){
    	System.out.println("Attaching the volume : " + volumeId + " to the instance : " + instanceId);
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
    	System.out.println("Detaching volume : " + volumeId + " from instance : " + instanceId);
    	DetachVolumeRequest dvr = new DetachVolumeRequest();
    	dvr.setVolumeId(volumeId);
    	dvr.setInstanceId(instanceId);
    	ec2.detachVolume(dvr);
    	
    	System.out.println("Volume Detached");
    }
    
    //Creates AMI of instance 
    public static String createAmi(String instanceId) {
    	System.out.println("Creating AMI from instance : " + instanceId);
    	CreateImageRequest cir = new CreateImageRequest();
		cir.setInstanceId(instanceId);
		cir.setName("AMI-" + instanceId);
		CreateImageResult createImageResult = ec2.createImage(cir);
		String createdImageId = createImageResult.getImageId();
    	
		return createdImageId;
    }
    
    //Restores instance from AMI and updates instance ID
    public static Instance restoreInstance(String imageId) {
    	try {
    		System.out.println("Restoring an Instance from AMI : " + imageId);
        	int minInstanceCount = 1; // create 1 instance
        	int maxInstanceCount = 1;
        	RunInstancesRequest rir = new RunInstancesRequest(imageId, minInstanceCount, maxInstanceCount);

        	rir.setKeyName("testElastic");
        	rir.withSecurityGroups("Shweta");
        	Placement p = new Placement();
            p.setAvailabilityZone("us-east-1a");
            rir.setPlacement(p);
        	
        	RunInstancesResult result = ec2.runInstances(rir);
        	
        	List<Instance> resultInstance = result.getReservation().getInstances();
        	for (Instance ins : resultInstance) {
        		return ins;
        	}
    	} catch (AmazonServiceException ase) {
    	System.out.println("Caught Exception: " + ase.getMessage());
    	System.out.println("Reponse Status Code: " + ase.getStatusCode());
    	System.out.println("Error Code: " + ase.getErrorCode());
    	System.out.println("Request ID: " + ase.getRequestId());
    	}
    	return null;
    }
    
    //Teminates the instance
    public static void terminateInstance(String instanceId) {
    	System.out.println("Terminating instance : " + instanceId);
		List<String> terminateInstances = new LinkedList<String>();
		terminateInstances.add(instanceId);
		TerminateInstancesRequest tir = new TerminateInstancesRequest(terminateInstances);
		ec2.terminateInstances(tir);
		
		System.out.println("Instance terminated");
	}

    public static void updateCounter() {
    	if(timerCounter == 9) {
    		timerCounter = 5;
    	}
    	else if(timerCounter == 5) {
    		timerCounter = 9;
    	}
    }
}
