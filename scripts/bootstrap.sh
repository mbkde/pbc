bootstrap() {
  if [ ! -f "/.dockerenv" ]; then
    docker run --rm -ti \
      -v "$HOME/.m2:/root/.m2" \
      -v "$(pwd):$(pwd)" \
      -w "$(pwd)" \
      --entrypoint "$0" \
      maven \
      "$@"
    exit
  fi
}
