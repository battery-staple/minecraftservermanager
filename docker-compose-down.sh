echo "Ending old server"
docker compose down
echo "Deleting old server image"
docker image rm -f minecraftservermanager-servermanager