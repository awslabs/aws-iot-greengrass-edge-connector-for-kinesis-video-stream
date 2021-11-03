import boto3
import yaml
import uuid
import time

iotsitewise = boto3.client('iotsitewise')
kinesisvideo = boto3.client('kinesisvideo')
secretsmanager = boto3.client('secretsmanager')

class resourceManager():

    def __init__(self):
        # Please do not change these values. Otherwise EdgeConnectorForKVS could not set start correctly.
        self.hubType = "EdgeConnectorForKVSHubAsset"
        self.cameraType = "EdgeConnectorForKVSCameraAsset"
        self.sitewise_asset_model_name_hub_prefix = "EdgeConnectorForKVSHubModel"
        self.sitewise_asset_model_name_camera_prefix = "EdgeConnectorForKVSCameraModel"

        self.hubs = []
        self.cameras = []
        self.existsHubAssets = []
        self.existsCameraAssets = []
        self.existingKVSStreams = []
        self.existingCameraAssetNameAndIdMap = {}
        self.existingHubAssetNameAndHierarchyIdMap = {}
        self.sitewise_asset_model_id_hub = ""
        self.sitewise_asset_model_id_camera = ""

    # Init needed resources for EdgeConnectorForKVS
    def init_resources(self):
        self.configuration_reader()
        self.check_or_create_sitewise_asset_model()
        self.list_exists_assets()
        self.check_or_create_sitewise_camera_assets()
        self.check_or_create_sitewise_hub_assets()

    # Read configuration from resource_configure.yml file
    def configuration_reader(self):
        with open("./resource_configure.yml", "r") as stream:
            try:
                configure = yaml.safe_load(stream)
                for key in configure:
                    assetConfigure = configure.get(key)
                    if assetConfigure.get("Type") is not None:
                        if assetConfigure.get("Type") == self.hubType:
                            self.hubs.append(assetConfigure)
                        elif assetConfigure.get("Type") == self.cameraType:
                            self.cameras.append(assetConfigure)
                        else:
                            raise TypeError(key + " unknown type! Please choose from EdgeConnectorForKVSHubAsset or EdgeConnectorForKVSCameraAsset")
                    else:
                        raise TypeError(key + " must have a Type!")
            except yaml.YAMLError as exc:
                print(exc)

    # First verify if EdgeConnectorForKVSHubModel and EdgeConnectorForKVSCameraModel exists
    # If not, create these models with 4 digital hash suffix
    # If yes, use the existing models
    def check_or_create_sitewise_asset_model(self):
        if(not self.is_sitewise_asset_model_exist(self.sitewise_asset_model_name_camera_prefix)):
            self.create_sitewise_camera_asset_model()
            print ("Created sitewise asset model for camera")
        else:
            print ("Already have sitewise asset model for camera, skip creation and use existing model")

        if(not self.is_sitewise_asset_model_exist(self.sitewise_asset_model_name_hub_prefix)):
            self.create_sitewise_hub_asset_model()
            print ("Created sitewise asset model for hub")
        else:
            print ("Already have sitewise asset model for hub, skip creation and use existing model")

    # Verify if given model exists
    def is_sitewise_asset_model_exist(self, model_name):
        nextTokenValue = ""
        while True:
            if not nextTokenValue:
                response = iotsitewise.list_asset_models(maxResults=50)
            else:
                response = iotsitewise.list_asset_models(maxResults=50, nextToken=nextTokenValue)
            for assetModel in response.get("assetModelSummaries"):
                if model_name in assetModel.get("name"):
                    if "Hub" in model_name:
                        self.sitewise_asset_model_id_hub = assetModel.get("id")
                    else:
                        self.sitewise_asset_model_id_camera = assetModel.get("id")
                    return True
            nextTokenValue = response.get("nextToken")
            if not nextTokenValue:
                break
        return False

    # Create EdgeConnectorForKVSHubModel with 4 digital hash suffix
    def create_sitewise_hub_asset_model(self):
        assetModelNameValue = 'EdgeConnectorForKVSHubModel-' + uuid.uuid4().hex[0:4]

        i = 0
        while i < 5:
            time.sleep(2)
            if self.is_sitewise_model_active(self.sitewise_asset_model_id_camera):
                response = iotsitewise.create_asset_model(
                    assetModelName = assetModelNameValue,
                    assetModelDescription='Hub Device for EdgeConnectorForKVS',
                    assetModelProperties=[
                        {
                            'name': 'HubName',
                            'dataType': 'STRING',
                            'type': {
                                'attribute': {
                                    'defaultValue': 'Hub Name'
                                },
                            }
                        },
                    ],
                    assetModelHierarchies=[
                        {
                            'name': 'ConnectedCameras',
                            'childAssetModelId': self.sitewise_asset_model_id_camera
                        },
                    ],
                )
                self.sitewise_asset_model_id_hub = response.get('assetModelId')
                break
            i = i+1

    # Create EdgeConnectorForKVSCameraModel with 4 digital hash suffix
    def create_sitewise_camera_asset_model(self):
        assetModelNameValue = 'EdgeConnectorForKVSCameraModel-' + uuid.uuid4().hex[0:4]
        sitewise_asset_model_name_camera = assetModelNameValue
        response = iotsitewise.create_asset_model(
            assetModelName = assetModelNameValue,
            assetModelDescription='Camera Device for EdgeConnectorForKVS',
            assetModelProperties=[
                {
                    'name': 'KinesisVideoStreamName',
                    'dataType': 'STRING',
                    'type': {
                        'attribute': {
                            'defaultValue': '<Replace with KVS stream name>'
                        },
                    }
                },
                {
                    'name': 'RTSPStreamSecretARN',
                    'dataType': 'STRING',
                    'type': {
                        'attribute': {
                            'defaultValue': '<Replace with Secret Arn including RTSP Stream URL>'
                        },
                    }
                },
                {
                    'name': 'LocalDataRetentionPeriodInMinutes',
                    'dataType': 'INTEGER',
                    'type': {
                        'attribute': {
                            'defaultValue': '60'
                        },
                    }
                },
                {
                    'name': 'LiveStreamingStartTime',
                    'dataType': 'STRING',
                    'type': {
                        'attribute': {
                            'defaultValue': '-'
                        },
                    }
                },
                {
                    'name': 'LiveStreamingDurationInMinutes',
                    'dataType': 'INTEGER',
                    'type': {
                        'attribute': {
                            'defaultValue': '0'
                        },
                    }
                },
                {
                    'name': 'CaptureStartTime',
                    'dataType': 'STRING',
                    'type': {
                        'attribute': {
                            'defaultValue': '-'
                        },
                    }
                },
                {
                    'name': 'CaptureDurationInMinutes',
                    'dataType': 'INTEGER',
                    'type': {
                        'attribute': {
                            'defaultValue': '0'
                        },
                    }
                },
                {
                    'name': 'VideoUploadRequest',
                    'dataType': 'STRING',
                    'type': {
                        'measurement': {},
                    }
                },
                {
                    'name': 'VideoUploadedTimeRange',
                    'dataType': 'DOUBLE',
                    'type': {
                        'measurement': {},
                    }
                },
                {
                    'name': 'VideoRecordedTimeRange',
                    'dataType': 'DOUBLE',
                    'type': {
                        'measurement': {},
                    }
                },
                {
                    'name': 'CachedVideoAgeOutOnEdge',
                    'dataType': 'DOUBLE',
                    'type': {
                        'measurement': {},
                    }
                },
            ],
        )
        self.sitewise_asset_model_id_camera = response.get('assetModelId')

    # List all existing hub and camera assets
    def list_exists_assets(self):
        nextTokenValue = ""
        while True:
            if not nextTokenValue:
                response = iotsitewise.list_assets(maxResults=100, assetModelId=self.sitewise_asset_model_id_hub)
            else:
                response = iotsitewise.list_asset_models(maxResults=100, nextToken=nextTokenValue, assetModelId=self.sitewise_asset_model_id_hub)
            for hubAsset in response.get("assetSummaries"):
                self.existsHubAssets.append(hubAsset.get('name'))
            nextTokenValue = response.get("nextToken")
            if not nextTokenValue:
                break

        while True:
            if not nextTokenValue:
                response = iotsitewise.list_assets(maxResults=100, assetModelId=self.sitewise_asset_model_id_camera)
            else:
                response = iotsitewise.list_asset_models(maxResults=100, nextToken=nextTokenValue, assetModelId=self.sitewise_asset_model_id_camera)
            for cameraAsset in response.get("assetSummaries"):
                self.existsCameraAssets.append(cameraAsset.get('name'))
                self.existingCameraAssetNameAndIdMap[cameraAsset.get('name')] = cameraAsset.get('id')
            nextTokenValue = response.get("nextToken")
            if not nextTokenValue:
                break

    # Get assetModelHierarchy Id of "ConnectedCameras" belongs to EdgeConnectorForKVSHubModel
    # This Hierarchy used to connect to cameras
    def get_hub_asset_model_hierarchy_id(self):
        response = iotsitewise.describe_asset_model(
            assetModelId= self.sitewise_asset_model_id_hub
        )
        for assetModelHierarchy in response.get('assetModelHierarchies'):
            if assetModelHierarchy.get('name') in 'ConnectedCameras':
                return assetModelHierarchy.get('id')
        return ''

    # First verify if configured hub sitewise asset exists
    # If not, create these asset with configured asset name
    # If yes, ship
    def check_or_create_sitewise_hub_assets(self):
        for hub in self.hubs:
            if hub.get("Name") in self.existsHubAssets:
                print ("Asset" + hub.get("Name") + " already exists, skip creation.")
            else:
                # create sitewise asset
                hub_asset_id = self.create_sitewise_asset(hub.get("Name"),  self.sitewise_asset_model_id_hub)
                properties = self.describe_sitewise_asset(hub_asset_id)
                print ("Created hub asset: " + hub.get("Name"))
                hierarchy_id_value = self.get_hub_asset_model_hierarchy_id()
                for key in hub:
                    if properties.get(key):
                        self.update_sitewise_property(hub_asset_id, properties.get(key), hub.get(key))
                    elif key == "ChildrenCameraSiteWiseAssetName":
                        for child_asset_name in hub.get(key):
                            if hierarchy_id_value:
                                i = 0
                                while i < 5:
                                    time.sleep(2)
                                    if self.is_sitewise_asset_active(hub_asset_id):
                                        iotsitewise.associate_assets(
                                            assetId=hub_asset_id,
                                            hierarchyId=hierarchy_id_value,
                                            childAssetId=self.existingCameraAssetNameAndIdMap.get(child_asset_name),
                                        )
                                        break
                                    i = i+1
                                if not self.is_sitewise_asset_active(hub_asset_id):
                                    print ("Hub asset create time out, did not associate Camera assets to it")

                            else:
                                print ("Cannot find assetModelHierarchies in hub model, cannot associate camera assets to it.")

                print ("Created hub asset: " + hub.get("Name"))

    # First verify if configured camera sitewise asset exists
    # If not, create these asset with configured asset name
    # If yes, ship
    def check_or_create_sitewise_camera_assets(self):
        for camera in self.cameras:
            if camera.get("Name") in self.existsCameraAssets:
                print ("Asset" + camera.get("Name") + " already exists, skip creation.")
            else:
                # create sitewise asset
                camera_asset_id = self.create_sitewise_asset(camera.get("Name"), self.sitewise_asset_model_id_camera)
                # create kvs stream if not exists
                if camera.get('KinesisVideoStreamName'):
                    self.check_or_create_kinesis_video_stream(camera.get('KinesisVideoStreamName'))
                else:
                    print ("Cannot find KinesisVideoStreamName for " + camera.get("Name") + ", will not update it")
                # create secret for rtsp url
                secret_arn = ''
                if camera.get('RTSPStream'):
                    secret_arn = self.create_secret(camera.get('RTSPStream'))
                    camera['RTSPStreamSecretARN'] = secret_arn
                else:
                    print ("Cannot find RTSPStream for " + camera.get("Name") + ", will not update it")
                properties = self.describe_sitewise_asset(camera_asset_id)
                for key in camera:
                    if properties.get(key):
                        self.update_sitewise_property(camera_asset_id, properties.get(key), camera.get(key))

                i = 0
                while i < 5:
                    time.sleep(2)
                    if self.is_sitewise_asset_active(camera_asset_id):
                        self.turn_on_notification_on_video_upload_request_measurement(camera_asset_id, properties)
                        break
                    i = i+1
                if not self.is_sitewise_asset_active(camera_asset_id):
                    print ("Camera asset create time out, did not turn on VideoUploadRequest measurement notification queue")
                self.existingCameraAssetNameAndIdMap[camera.get("Name")] = camera_asset_id
                print ("Created camera asset: " + camera.get("Name"))

    # Describe SiteWise asset
    def describe_sitewise_asset(self, sitewise_asset_id):
        response = iotsitewise.describe_asset(
            assetId=sitewise_asset_id
        )
        properties = {}
        for assetProperty in response.get('assetProperties'):
            properties[assetProperty.get('name')] = assetProperty.get('id')
        return properties

    # Update SiteWise property
    def update_sitewise_property(self, assetId, propertyId, propertyValues):
        property_pairs = self.generate_property_values_content(propertyValues)
        response = iotsitewise.batch_put_asset_property_value(
            entries=[
                {
                    'entryId': assetId + uuid.uuid4().hex[0:4],
                    'assetId': assetId,
                    'propertyId': propertyId,
                    'propertyValues': [
                        {
                            'value': {
                                property_pairs[0]: property_pairs[1]
                            },
                            'timestamp': {
                                'timeInSeconds': int(time.time()),
                                'offsetInNanos': 0
                            },
                            'quality': 'GOOD'
                        },
                    ]
                },
            ]
        )

    # Auto match given propertyValues into target sitewise value type
    def generate_property_values_content(self, propertyValues):
        result = {}
        if type(propertyValues) == str:
            result['stringValue'] = propertyValues
        elif type(propertyValues) == int:
            result['integerValue'] = propertyValues
        elif type(propertyValues) == float:
            result['doubleValue'] = propertyValues
        else:
            result['booleanValue'] = propertyValues
        dict_pairs = result.items()
        pairs_iterator = iter(dict_pairs)
        return next(pairs_iterator)

    # Create SiteWise asset by modelId
    def create_sitewise_asset(self, assetName, modelId):
        i = 0
        while i < 5:
            time.sleep(2)
            if self.is_sitewise_model_active(modelId):
                response = iotsitewise.create_asset(
                    assetName=assetName,
                    assetModelId=modelId,
                )
                return response.get('assetId')
            i = i+1
        if not self.is_sitewise_model_active(modelId):
            print ("Model create time out, fail to create related asset")

    # Update Camera asset's "VideoUploadRequest" measurement, turn on notification
    def turn_on_notification_on_video_upload_request_measurement(self,camera_assetId, properties):
        propertyId = properties.get("VideoUploadRequest")
        response = iotsitewise.update_asset_property(
            assetId=camera_assetId,
            propertyId=propertyId,
            propertyNotificationState='ENABLED',
        )

    # Verify if the model created finished and is in 'ACTIVE' status
    def is_sitewise_model_active(self, sitewise_modelId):
        response = iotsitewise.describe_asset_model(
            assetModelId = sitewise_modelId
        )
        return response.get('assetModelStatus').get('state') == 'ACTIVE'

    # Verify if the asset created finished and is in 'ACTIVE' status
    def is_sitewise_asset_active(self, sitewise_assetId):
        response = iotsitewise.describe_asset(
            assetId=sitewise_assetId
        )
        return response.get('assetStatus').get('state') == 'ACTIVE'

    # Verify if the asset created finished and is in 'ACTIVE' status
    def check_or_create_kinesis_video_stream(self, streamName):
        response = kinesisvideo.list_streams(
            StreamNameCondition={
                'ComparisonOperator': 'BEGINS_WITH',
                'ComparisonValue': streamName
            }
        )

        if len(response.get('StreamInfoList')) == 0 :
            response = kinesisvideo.create_stream(
                DeviceName=streamName,
                StreamName=streamName,
                DataRetentionInHours=168,
            )
            print ("Created KVS video streamName: " + streamName)
        else:
            print ("StreamName: " + streamName + " already exist, skip creation")

    # Create secret
    def create_secret(self, rtspUrl):
        response = secretsmanager.create_secret(
            Name='CameraSecret-' + uuid.uuid4().hex[0:12],
            Description='CameraSecret',
            SecretString='{"RTSPStreamURL": "' + rtspUrl + '"}',
        )
        return response.get('ARN')

if __name__ == "__main__":
    manager = resourceManager()
    manager.init_resources()
