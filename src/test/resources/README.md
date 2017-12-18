### Generate the server keystore

```keytool -genkey -alias webserver -keyalg RSA -keystore keystore.jks -dname "CN=localhost" -storepass changeit -keypass changeit```

### Generate certificates with CRL

Instructions are based primary on [this with some modifications](https://netnix.org/2014/04/06/self-signed-ca-with-crl-for-java-code-signing/)

  * Initialise Certificate Authority (CA)
```
mkdir -p ca/certs
touch ca/index.txt
echo "01" >> ca/serial
vi openssl.cfg
``` 
  * Generate a Certificate Authority (CA)
```
openssl req -new -x509 -newkey rsa:4096 -keyout ca/ca.key -out ca/ca.crt -days 9125 -extensions v3_ca -config openssl.cfg
```
  * Generate a Certificate Signing Request (CSR)
```
openssl req -new -newkey rsa:4096 -nodes -keyout localhost.key -out localhost.csr -config openssl.cfg
```
  * Sign Certificate Signing Request (CSR) with Certificate Authority (CA)
```
openssl ca -create_serial -days 1095 -in localhost.csr -out localhost.crt -notext -extensions v3_req_sign -config openssl.cfg
```
  * Generate Certificate Revocation List (CRL)
```
openssl ca -gencrl -crldays 30 -out ca/ca.crl -keyfile ca/ca.key -cert ca/ca.crt -config openssl.cfg
```
  * Revoke a Certificate and Generate an Updated CRL
```
openssl ca -keyfile ca/ca.key -cert ca/ca.crt -revoke ca/certs/01.pem -config openssl.cfg
openssl ca -gencrl -crldays 30 -out ca/ca.crl -keyfile ca/ca.key -cert ca/ca.crt -config openssl.cfg
openssl crl -in ca/ca.crl -text -noout
```
  * Combine Certificate and Key into PKCS12 Format
```
openssl pkcs12 -export -in localhost.crt -inkey localhost.key -out localhost.pfx
```
  * Import Certificate (and CA Public Certificate) into Java KeyStore
```
keytool -importcert -keystore keystore.jks -file ca/ca.crt
keytool -importkeystore -destkeystore keystore.jks -srckeystore localhost.pfx -srcstoretype PKCS12
keytool -list -v -keystore keystore.jks
```
