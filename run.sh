git pull
rm ticketingsystem/*.class
if [ -e "./trace1.sh"];
then
    ./trace1.sh
    java ticketingsystem/Trace1
else
    ./trace.sh
    java ticketingsystem/Trace > trace
    java -jar "./verify.jar"
fi
