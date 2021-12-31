# Init Solr (From https://github.com/docker-solr/docker-solr)
mkdir var-solr

# Chown necessary on Linux, not Mac.
sudo chown -R 8983:8983 var-solr

# Start opencola from docker-compose.yml (http://localhost:8983/)
docker-compose up -d

# Maunual way
# docker run -d -v "$PWD/solrdata:/var/solr" -p 8983:8983 --name my_solr solr:8

# See if id can be data instead of string
# Make single call: https://solr.apache.org/guide/8_10/schema-api.html#multiple-commands-in-a-single-post
# Copy to "text" field? See "techproducts"
# use "_t" (et. al) names?
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"id", "type":"text_general", "required":true, "multiValued":false, "stored":true}}' http://localhost:8983/solr/opencola/schema
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"name", "type":"text_general", "multiValued":false, "stored":true}}' http://localhost:8983/solr/opencola/schema
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"description", "type":"text_general", "multiValued":false, "stored":true}}' http://localhost:8983/solr/opencola/schema
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"tags", "type":"text_general", "multiValued":true, "stored":true}}' http://localhost:8983/solr/opencola/schema
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"trust", "type":"pfloat", "multiValued":false, "stored":true}}' http://localhost:8983/solr/opencola/schema

# Check types and other params
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"like", "type":"boolean", "multiValued":false, "stored":true}}' http://localhost:8983/solr/opencola/schema
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"rating", "type":"pfloat", "multiValued":false, "stored":true}}' http://localhost:8983/solr/opencola/schema
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"dateTime", "type":"pfloat", "required":true, "multiValued":false, "stored":true}}' http://localhost:8983/solr/opencola/schema


# http://localhost:8983/solr/admin/cores?action=CREATE&name=test
# http://localhost:8983/solr/admin/cores?action=RELOAD&core=test
# http://localhost:8983/solr/admin/cores?action=STATUS&core=test
# http://localhost:8983/solr/admin/cores?action=UNLOAD&core=test

# Post some sample data
docker exec -it opencola-solr-1 post -c opencola example/exampledocs/manufacturers.xml

# For following Solr in Action, this command should be run ON the image in the
# example docs directory
post -c opencola *.xml

# Solr 8.10 tutorial at https://solr.apache.org/guide/8_10/solr-tutorial.html
# General guide that covers schema, config, etc. https://solr.apache.org/guide/8_10/

# Add a name filed to schema and make sure it's text
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"name", "type":"text_general", "multiValued":false, "stored":true}}' http://localhost:8983/solr/films/schema

# Add a copy field to schema so that all text is searchable without specifying
# explicit fields
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-copy-field" : {"source":"*","dest":"_text_"}}' http://localhost:8983/solr/films/schema


###############################################################################################
# Tika
###############################################################################################
# Tika in docker
docker run -d -p 9998:9998 apache/tika:2.1.0

# Building Tika locally (https://github.com/apache/tika)
git clone https://github.com/apache/tika.git
cd tika
git checkout 1.22
mvn clean install
