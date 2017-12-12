#Adapted from sockets.py from Computer Networking 

#import socket module
from socket import *
import sys
serverSocket = socket(AF_INET, SOCK_STREAM)
#Prepare a sever socket
#Fill in start
host = gethostbyname(gethostname())

port = int(sys.argv[2])
file_name = sys.argv[1]

serverSocket.bind((host, port))
serverSocket.listen(5)
#Fill in end
while True:
    #Establish the connection
    print 'Ready to serve...'
    connectionSocket, addr = serverSocket.accept()
    try:
        message = connectionSocket.recv(4096)
        print 'message: ', message
        filename = file_name
        print 'filename: ', filename
        f = open(filename[1:])
        outputdata = f.read()
        print 'output data: ', outputdata
        
        for i in range(0, len(outputdata)):
            connectionSocket.send(outputdata[i])
        connectionSocket.close()
    except IOError:
        #Send response message for file not found
        #Fill in start
        print "Error"
        #Fill in end
#Close client socket
#Fill in start
serverSocket.close()
#Fill in end