An opensource airline game. 

Forked from https://www.airline-club.com/
Live at https://myfly.club/


![Screenshot 1](https://user-images.githubusercontent.com/2895902/74759887-5a966380-522e-11ea-9e54-2252af63d5ea.gif)

## Dependencies
- Java openjdk 11
- MySQL 8
- Sbt

## Setup
1. Create MySQL database matching values defined [here](https://github.com/patsonluk/airline/blob/master/airline-data/src/main/scala/com/patson/data/Constants.scala#L184).
1. Define sbt JVM minimum resoures by setting `export SBT_OPTS="-Xms2g -Xmx8g"` in your CLI. Depending how you're running Java, you may need to enable more memory elsewhere too.
1. Navigate to `airline-data` and run `sbt publishLocal`. If you see [encoding error](https://github.com/patsonluk/airline/issues/267), add character-set-server=utf8mb4 to your /etc/my.cnf and restart mysql. it's a unicode characters issue, see https://stackoverflow.com/questions/10957238/incorrect-string-value-when-trying-to-insert-utf-8-into-mysql-via-jdbc
1. In `airline-data`, run `sbt run`, 
    1. Then, choose the one that runs `MainInit`. It will take awhile to init everything.
1. Set `google.mapKey` in [`application.conf`](https://github.com/patsonluk/airline/blob/master/airline-web/conf/application.conf#L69) with your google map API key value. Be careful with setting budget and limit, google gives some free credit but it CAN go over and you might get charged!
1. For the "Flight search" function to work, install elastic search version 7.x(The latest version is not compatible), see https://www.elastic.co/guide/en/elasticsearch/reference/current/install-elasticsearch.html . For windows, I recommand downloading the zip archive and just unzip it - the MSI installer did not work on my PC
1. For airport image search and email service for user pw reset - refer to https://github.com/patsonluk/airline/blob/master/airline-web/README
1. Now run the background simulation by staying in `airline-data`, run `sbt run`, select option `MainSimulation`. It should run the background simulation.
1. Open another terminal, navigate to `airline-web`, run the web server by `sbt run`
1. The application should be accessible at `localhost:9000`

## Alternate Docker Setup
1. Install Docker & Docker-compose
1. run `cp docker-compose.override.yaml.dist docker-compose.override.yaml` and then edit the new file with your preferred ports. Mysql only has to have exposed ports if you like to connect from outside docker
   1. If you plan to use this anything else than for development, adjust the credentials via environment variables
2. start the stack with `docker compose up -d` and confirm both containers are running
3. open a shell inside the container via `docker compose exec airline-app bash`
4. run the init scripts:
   1. `sh init-data.sh` (might need to run it a couple of times because migration seems to be spotty)
5. To boot up both front and backend, use the start scripts `sh start-data.sh` and `sh start-web.sh` in separate sessions
6. The application should be accessible at your hosts ip address and port 9000. If docker networks aren't limited by firewalls or network settings, it should be available without any reverse-proxying. (Dev only!)


## Nginx Proxy w/ Cloudflare HTTPS

In Cloudflare go to your domain and then SSL/TLS > Origin Server. Click Create Certificate > Generate private key and CSR with Cloudflare > Drop down choose ECC > Create

Save your Origin Certificate and your Private Key to a file. Example:

Orgin Certificate: domain.com.crt

Private Key: domain.com.key

Example nginx virtualhost conf file:

```
server {

  listen 443 ssl http2;
  listen [::] ssl http2;
  server_name domain.com;

  ssl_certificate      /usr/local/nginx/conf/ssl/domain.com/domain.com.crt;
  ssl_certificate_key  /usr/local/nginx/conf/ssl/domain.com/domain.com.key;

  add_header X-Frame-Options SAMEORIGIN;
  add_header X-Xss-Protection "1; mode=block" always;
  add_header X-Content-Type-Options "nosniff" always;
  add_header Referrer-Policy "strict-origin-when-cross-origin";

  access_log /home/nginx/domains/domain.com/log/access.log combined buffer=256k flush=5m;
  error_log /home/nginx/domains/domain.com/log/error.log;

  location /assets  {
    alias    /home/airline/airline-web/public/;
    access_log on;
    expires 30d;
  }

  location / {
    proxy_pass http://localhost:9000;
    proxy_pass_header Content-Type;
    proxy_read_timeout     60;
    proxy_connect_timeout  60;
    proxy_redirect         off;

    # Allow the use of websockets
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection 'upgrade';
    proxy_set_header Host $host;
    proxy_cache_bypass $http_upgrade;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  }

}
```

## Attribution
1. Some icons by [Yusuke Kamiyamane](http://p.yusukekamiyamane.com/). Licensed under a [Creative Commons Attribution 3.0 License](http://creativecommons.org/licenses/by/3.0/)
