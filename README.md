# Universal Resolver Driver for Health Pass 

## Maven build and run in local environment:

Make sure that you are in the driver root directory `HealthPass/uni-resolver-driver-did-hpass`

### Create JAR file:

```
mvn clean install package -N
```

### Create WAR file:

```
mvn clean install package -N -P war
```

### Start Jetty webserver locally

```
mvn jetty:run -Djetty.port=8090 -P war
```

## Build and run in docker container:

### Build docker container:

Provide http port `JETTY_HTTP_PORT` of the Jetty http port via command line argument.
```
docker build --build-arg JETTY_HTTP_PORT=8090 -f ./docker/Dockerfile . -t universalresolver/driver-did-hpass
```

This `JETTY_HTTP_PORT` argument is also used to create a docker environment variable `JETTY_HTTP_PORT` that is used to start Jetty in the docker container with the correct http port.

### Run docker image (start jetty webserver):

At startup, the webserver will try to load the .env file in the root directory. 
If this file doesn't exist, it will look for standard environment variables.

If environment variables are preconfigured or stored in `.env` file in the docker root directory, run:
```
docker run -p 8090:8090 universalresolver/driver-did-hpass
```

The environment variables provided in the .env file will take precedence over preconfigured environment variables.
Environment variables can also be provided during the docker container startup as command line parameters and will take precedence.
This option is preferable for a deployment-specific set of environment variables (e.g. stored in `.env.dev` file):

```
docker run --env-file=.env.dev -p 8090:8090 universalresolver/driver-did-hpass 
```

The environment variables can also be passed directly when starting the docker container. Note that strings with special characters (e.g. passwords) should be passed with single quotes (e.g. `'password'`):
```
docker run -e UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL=https://dev1.wh-hpass.dev.watson-health.ibm.com/api/v1/hpass/issuers/ -e UNIRESOLVER_DRIVER_USER='uniresolver@poc.com' -e UNIRESOLVER_DRIVER_PASSWORD='password' -p 8090:8090 universalresolver/driver-did-hpass
```

## Driver Environment Variables

The driver recognizes the following environment variables:
```
UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL
```

Specifies the URL(s) of the Health Node from which the DID document associated with a `did` should be retrieved.

Note that multiple URLs can be provided for `UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL`. The URLs will be separated by commas.

```
UNIRESOLVER_DRIVER_DID_REGISTRY_URL
```

Specifies the URL(s) of the Health Pass Environment Registry. If the environment variable `UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED` is set to `"true"`, the environments stored in the registry will be retrieved based on the `did` and the URL(s) of the health node(s), from which the DID document should be retrieved, will be used for retrieving the DID document.

Note that multiple URLs can be provided for `UNIRESOLVER_DRIVER_DID_REGISTRY_URL`. The URLs will be separated by commas.

```
UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED
```

Specifies if the DID document should be retrieved directly from the URL(s) specified in `UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL`
or through the URL provided in the Health Pass Environment Registry for a given `did`.

- Retrieve from Health Node URL(s): `UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED=false`
- Retrieve from URL(s) specified in Health Pass Environment Registry: `UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED=true`

If `UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED` is not set, it will be assigned to `false` as default within the driver, and requests will be sent to the URL(s) of the Health Node.

```
UNIRESOLVER_DRIVER_AUTH_ENABLED
```

Specifies if authorithation is required to get access to Health pass and to retrieve an authorithation token.

```
UNIRESOLVER_DRIVER_AUTH_LOGIN_URL
```

Specifies the login URL if authorithation is required to get access to Health pass and to retrieve an authorithation token.

```
UNIRESOLVER_DRIVER_USER
```

Specifies the username of the user trying to login to Health pass and retrieve an authorithation token.

```
UNIRESOLVER_DRIVER_PASSWORD
```

Specifies the password of the user trying to login to Health pass and retrieve an authorithation token.

## Web interface

The web interface exposes two endpoints, `/1.0/identifiers/` and `/1.0/properties/`.

### /1.0/identifiers/

The endpoint `/1.0/identifiers/` queries the DID document for a specified `did` with the following syntax:

```
curl -X GET http://{host}:{port}/1.0/identifiers/{did}
```

Example query:

```
curl -X GET http://localhost:8090/1.0/identifiers/did:hpass:aaaa172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6aaaa
```

Example response:

```
{
    "didDocument": {
        "@context": [
            "https://www.w3.org/ns/did/v1"
        ],
        "id": "did:hpass:aaaa172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6aaaa",
        "verificationMethod": [
            {
                "type": [
                    "JsonWebKey2020"
                ],
                "id": "did:hpass:f18c172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d61678#key-1",
                "publicKeyJwk": {
                    "crv": "P-256",
                    "kty": "EC",
                    "x": "afvXSTBvOIEDQ2DyRNNlI6cXf-b0-sdftMV8vgWGc_o",
                    "y": "OsGvVOFy12ENXovet217Fo4RG7v-uzWwKnk_IEmrcw0"
                },
                "controller": "did:hpass:f18c172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d61678"
            }
        ]
    },
    "content": null,
    "contentType": "application/did+ld+json",
    "didResolutionMetadata": null,
    "didDocumentMetadata": {
        "created": "2021-02-13T16:06:55Z",
        "updated": "2021-02-13T16:06:55Z"
    }
}
```

### /1.0/properties/

The endpoint `/1.0/properties/` queries the driver environment variables that are read and assigned during driver startup:

```
curl -X GET http://{host}:{port}/1.0/properties
```

Example query:

```
curl -X GET http://localhost:8090/1.0/properties
```

Example response:

```
{
    "UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL": "https://dev1.wh-hpass.dev.watson-health.ibm.com2/api/v1/hpass/issuers/,https://dev1.wh-hpass.dev.watson-health.ibm.com/api/v1/hpass/issuers/",
    "UNIRESOLVER_DRIVER_DID_REGISTRY_URL": "http://10.0.0.9:9000/api/v1/environments/,http://abc:1000",
    "UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED": "false",
    "UNIRESOLVER_DRIVER_AUTH_ENABLED": "true",
    "UNIRESOLVER_DRIVER_AUTH_LOGIN_URL": "https://dev1.wh-hpass.dev.watson-health.ibm.com/api/v1/hpass/users/login",
    "UNIRESOLVER_DRIVER_USER": "uniresolver@poc.com",
    "UNIRESOLVER_DRIVER_PASSWORD": "password"
}
```



# Integration of Health Pass driver into universal resolver

## Contributing and integrating the driver

```
https://github.com/decentralized-identity/universal-resolver/blob/main/docs/driver-development.md#how-to-test-a-driver-locally
```

- Clone Universal Resolver repo:
```git
git clone https://github.com/decentralized-identity/universal-resolver
cd universal-resolver/
```

- Modify the following files: 
  - .env
  - config.json
  - docker-compose.yml
  - Readme.md
  
- Pull docker images
  ```
  docker-compose -f docker-compose.yml pull
  ```

- Build `uni-resolver-web` locally:

   ```
   docker build -f ./uni-resolver-web/docker/Dockerfile . -t universalresolver/uni-resolver-web
   ```

- Run the uni-resolver-web locally:

   ```
   docker-compose -f docker-compose.yml up
   ```

## Push docker images to Docker hub

```
docker build -f ./docker/Dockerfile . -t uni-resolver-driver-did-hpass
docker tag uni-resolver-driver-did-hpass:latest ibmcom/uni-resolver-driver-did-hpass:1.0
docker tag uni-resolver-driver-did-hpass:latest ibmcom/uni-resolver-driver-did-hpass:latest
(docker login -u <user>)
docker push ibmcom/uni-resolver-driver-did-hpass:1.0
docker push ibmcom/uni-resolver-driver-did-hpass:latest
```
