echo "*** whirlpool-server build ***"
read -p 'Git username: ' login
read -sp 'Git token: ' password
echo ''

rm -Rf build
mkdir build
cd build

mkdir tmp
cd tmp

# whirlpool-protocol
git clone https://$login:$password@github.com/Samourai-Wallet/whirlpool-protocol.git
if [ "$?" -ne 0 ]; then
  echo 'git clone error'; exit $rc
fi

cd whirlpool-protocol
mvn install
if [ "$?" -ne 0 ]; then
  echo 'mvn install error'; exit $rc
fi
cd ..

# whirlpool-client
git clone https://$login:$password@github.com/Samourai-Wallet/whirlpool-client.git
if [ "$?" -ne 0 ]; then
  echo 'git clone error'; exit $rc
fi
cd whirlpool-client
mvn install -Dmaven.test.skip=true
if [ "$?" -ne 0 ]; then
  echo 'mvn install error'; exit $rc
fi
cd ..

# whirlpool-server
git clone https://$login:$password@github.com/Samourai-Wallet/whirlpool-server.git
if [ "$?" -ne 0 ]; then
  echo 'git clone error'; exit $rc
fi
cd whirlpool-server
mvn install -Dmaven.test.skip=true
if [ "$?" -ne 0 ]; then
  echo 'mvn install error'; exit $rc
fi
cd ..

# move jar to ./build
cd ../..
cd build/tmp/whirlpool-server/target/
for filename in whirlpool-server-*.jar; do
    jarname=$filename
done
cd ../../../..

echo "Copying ${jarname} to ./build"
cp build/tmp/whirlpool-server/target/${jarname} build/

# cleanup
rm -Rf build/tmp

echo "Build success: ./build/${jarname}"