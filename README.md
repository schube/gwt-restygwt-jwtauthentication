# gwt-restygwt-jwtauthentication

Implementation of JWT Authentification and automatically fetching refresh token in GWT with RestyGWT

This project shows how I am using RestGWT in a GWT application.

I uploaded the relevant code and left all the application details, because they are not important here (also, I am not allowed to share the code).
I use this code in production, and it works well with one flaw. See below.

This is how it works:

* User logs in at server with username/password and retrieves a JWT access token and refresh token.
* The access has a very short lifespan, like 5 minutes.
* The refresh token has a long lifespan, like 4 hours.

Now, when the GWT client app sends a request to the server, it sends the request itself and adds the JWT access token in the header.
The client DOES NOT check if the access token is still valid.

Now, the server checks if the access token is still valid.
If yes, everything is fine, the server answers the request and sends a repsonse with the data requested.

But if the access token is not valid any more, the server answers with HTTP status code "401 Unauthorized".
Now, the client knows that this status code means, the access token is not valid and fetches a new access token by using the refresh token.
The server sends a new access token and the client re-sends the initial request.

This is the sequence:

~~~
+---------+                               +---------+
| Client  |                               | Server  |
+---------+                               +---------+
     |                                         |
     | Send request with access token AT1      |
     |---------------------------------------->|
     |         ------------------------------\ |
     |         | Access token is still valid |-|
     |         |-----------------------------| |
     |                                         |
     |              Send response with payload |
     |<----------------------------------------|
     |                                         |
~~~


~~~
+---------+                                                                +---------+
| Client  |                                                                | Server  |
+---------+                                                                +---------+
     |                                                                          |
     | Send request with access token                                           |
     |------------------------------------------------------------------------->|
     |                                               -------------------------\ |
     |                                               | Access token not valid |-|
     |                                               |------------------------| |
     |                                                                          |
     |                                         Send response "401 Unauthorized" |
     |<-------------------------------------------------------------------------|
     |                                                                          |
     | Send request to get new access token                                     |
     |------------------------------------------------------------------------->|
     |                                                                          |
     |      Send response with new access token, invalidating old refresh token |
     |<-------------------------------------------------------------------------|
     | ----------------------------------------------------\                    |
     |-| The initial request is prepared for sending again |                    |
     | |---------------------------------------------------|                    |
     |                                                                          |
     | Send request with new access token                                       |
     |------------------------------------------------------------------------->|
     |                                                                          |
     |                                               Send response with payload |
     |<-------------------------------------------------------------------------|
     |                                                                          |
~~

# Problem

Now for the problem:
If the client sends two or more requests in parallel to the server with an expired access token, the first request successfully retrieves a new access token from the server and the client can repeat this request.
But the second request fails, because the server will deny to create a new access token because also the refresh token is not valid any more (since request 1 triggered a new refresh token).
See sequence below:

~~~
+-----------+                                               +-----------+                                                                     +---------+                                                                                        
| Request1  |                                               | Request2  |                                                                     | Server  |                                                                                        
+-----------+                                               +-----------+                                                                     +---------+                                                                                        
      |                                                           |                                                                                |                                                                                             
      | Step 1:  Send request 1 with access token "AT1"           |                                                                                |                                                                                             
      |------------------------------------------------------------------------------------------------------------------------------------------->|                                                                                             
      |                                                           |                                                                                |                                                                                             
      |                                                           | Step 2: Send request 2 with access token "AT1"                                 |                                                                                             
      |                                                           |------------------------------------------------------------------------------->|                                                                                             
      |                                                           |                                              --------------------------------\ |                                                                                             
      |                                                           |                                              | Access token  "AT1" not valid |-|                                                                                             
      |                                                           |                                              |-------------------------------| |                                                                                             
      |                                                           |                                                                                |                                                                                             
      |                                                           |                                     Step 3: Send response 1 "401 Unauthorized" |                                                                                             
      |<-------------------------------------------------------------------------------------------------------------------------------------------|                                                                                             
      |                                                           |                                                                                |                                                                                             
      |                                                           |                                     Step 4: Send response 2 "401 Unauthorized" |                                                                                             
      |                                                           |<-------------------------------------------------------------------------------|                                                                                             
      |                                                           |                                                                                |                                                                                             
      | Step 5: Send request to get new access token by using refresh token "RT1"                                                                  |                                                                                             
      |------------------------------------------------------------------------------------------------------------------------------------------->|                                                                                             
      |                                                           |                                                                                |                                                                                             
      |                                                           |                                                                                | Step 6: Creates new access token "AT2" and refresh token "RT2", invalidates old token "RT1" 
      |                                                           |                                                                                |-------------------------------------------------------------------------------------------- 
      |                                                           |                                                                                |                                                                                           | 
      |                                                           |                                                                                |<------------------------------------------------------------------------------------------- 
      |                                                           |                                                                                |                                                                                             
      |                                                           | Step 7: Send request to get new access token by using refresh token "RT1"      |                                                                                             
      |                                                           |------------------------------------------------------------------------------->|                                                                                             
      |                                                           |                                                                                |                                                                                             
      |                                                           |          Step 8: Sends error, since refresh token "RT1" is not valid any more. |                                                                                             
      |                                                           |<-------------------------------------------------------------------------------|                                                                                             
      |                                                           | -------------------------------------------------------\                       |                                                                                             
      |                                                           |-| @@@@ Application displays error message to user !!!! |                       |                                                                                             
      |                                                           | |------------------------------------------------------|                       |                                                                                             
      |                                                           |                                                                                |                                                                                             
      |                                                           |  Step 9: Send response with new access token "AT2" and new refresh token "RT2" |                                                                                             
      |<-------------------------------------------------------------------------------------------------------------------------------------------|                                                                                             
      | ----------------------------------------------------\     |                                                                                |                                                                                             
      |-| The initial request is prepared for sending again |     |                                                                                |                                                                                             
      | |---------------------------------------------------|     |                                                                                |                                                                                             
      |                                                           |                                                                                |                                                                                             
      | Step 10: Send request with new access token "AT2"         |                                                                                |                                                                                             
      |------------------------------------------------------------------------------------------------------------------------------------------->|                                                                                             
      |                                                           |                                                                                |                                                                                             
      |                                                           |                                            Step 11: Send response with payload |                                                                                             
      |<-------------------------------------------------------------------------------------------------------------------------------------------|                                                                                             
      |                                                           |                                                                                |    
~~~      


Step 8 in this sequence creates a client error, which should be avoided.

Note: 
The request/responses can by in any order:


~~~
+-----------+ +-----------+ +-----------+ +-----------+                               +---------+
| Request1  | | Request2  | | Request3  | | Request4  |                               | Server  |
+-----------+ +-----------+ +-----------+ +-----------+                               +---------+
      |             |             |             |  --------------------------------------\ |
      |             |             |             |  | Request/Response in "correct" order |-|
      |             |             |             |  |-------------------------------------| |
      |             |             |             |                                          |
      | Send request 1            |             |                                          |
      |----------------------------------------------------------------------------------->|
      |             |             |             |                                          |
      |             | Send request 2            |                                          |
      |             |--------------------------------------------------------------------->|
      |             |             |             |                                          |
      |             |             |             |                          Send response 1 |
      |<-----------------------------------------------------------------------------------|
      |             |             |             |                                          |
      |             |             |             |                          Send response 2 |
      |             |<---------------------------------------------------------------------|
      |             |             |             |----------------------------------------\ |
      |             |             |             || Request/Response in "incorrect" order |-|
      |             |             |             ||---------------------------------------| |
      |             |             |             |                                          |
      |             |             | Send request 3                                         |
      |             |             |------------------------------------------------------->|
      |             |             |             |                                          |
      |             |             |             | Send request 4                           |
      |             |             |             |----------------------------------------->|
      |             |             |             |                                          |
      |             |             |             |                          Send response 4 |
      |             |             |             |<-----------------------------------------|
      |             |             |             |                                          |
      |             |             |             |                          Send response 3 |
      |             |             |<-------------------------------------------------------|
      |             |             |             |                                          |
~~~