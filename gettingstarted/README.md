## Summary
Edge Connector For Kinesis Video Stream component stores its configuration in AWS IoT SiteWise, which is an AWS service that models and stores industrial data. In AWS IoT SiteWise, assets represent objects such as devices, equipment, or groups of other objects. 

This script will help you the needed AWS resources to use Edge Connector For Kinesis Video Stream. Use this script, you can create one or more AWS IoT SiteWise asset for each Greengrass core device (the hub) and for each IP camera connected to each core device which been managed in the hub's network. Each camera SiteWise asset has properties that you configure to control features such as live streaming, on-demand upload, and local caching. 

This script will help you generate you needed SiteWise, Kinesis Video Stream. This script will also help you to specify the URL for each camera, and create a secret in AWS Secrets Manager that contains the URL of the camera, including a username and password if the camera requires authentication. 

Note: These instructions have primarily been tested for Mac/Linux environments.

## Prerequisites
- An AWS account
- Install [Python3](https://www.python.org/downloads/)
- Install [boto3](https://boto3.amazonaws.com/v1/documentation/api/latest/guide/quickstart.html)
  - Quick install: ```pip install boto3```
  - Have [AWS CLI](https://aws.amazon.com/cli/) installed, use ```aws configure``` command to configure your credentials file
  - This script needs the following minimum IAM permission policies:
     - kinesisvideo:CreateStream
     - kinesisvideo:ListStreams
     - secretsmanager:CreateSecret
     - iotsitewise:AssociateAssets     
     - iotsitewise:BatchPutAssetPropertyValue
     - iotsitewise:CreateAsset
     - iotsitewise:CreateAssetModel
     - iotsitewise:DescribeAsset
     - iotsitewise:DescribeAssetModel          
     - iotsitewise:ListAssets
     - iotsitewise:ListAssetModels     
     - iotsitewise:UpdateAssetProperty           
- Install [pyyaml](https://github.com/yaml/pyyaml)
  - Quick install: ```pip install pyyaml```   

## How to use
Step 1. Edit resource_configure.yml
  - Follow the instructions in the file to add your needed hub and camera setting
  - Each hub may manage multiple cameras. You can create multiple camera assets then add their "Name" (which will be the SiteWise asset name for this camera) into hub's "ChildrenCameraSiteWiseAssetName" array. In this way the script will create needed assets and do the association.  
   
Step 2. run ```python ./resourceManager.py```

Step 3. After execute, script will generated following resources:
  - SiteWise asset model for hubs and cameras
  - SiteWise assets for hubs and cameras
  - If configured Kinesis Video Stream name does not exist, create the stream
  - Secret ARN to store camera's RTSP url.
  
After these steps, please go to AWS SiteWise console and check the new created SiteWise asset for hub device. Using it's asset Id to configure Edge connector for Kinesis Video Streams and finish the deployment.

## Notes
After creation, please remove the content in the resource_configure.yml to preventing information leaking.