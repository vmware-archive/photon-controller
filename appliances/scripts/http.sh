# Download a file, first param is URL
function download_file() {
  # Use aria2c for faster downloads if installed
  # This is mainly for friendlier and faster local development on Mac
  if hash aria2c 2> /dev/null; then
    aria2c -s16 -x16 --allow-overwrite --all-proxy="" $1
  else
    wget --no-proxy --no-check-certificate -nv -N $1
  fi
}

# Test if URL exists using HTTP HEAD, first param is URL
function url_exists() {
  # Perform HTTP HEAD and check for 2xx or 3xx status
  result=$(curl -s --head $1 | head -n 1 | awk '{ print $2 }' | grep "[23]")
  if [ ! -z $result ]; then
    # 0 is success, non-zero is error
    return 0
  fi
  return 1
}
