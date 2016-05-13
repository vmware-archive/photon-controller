export NO_PORT_FORWARDING=1

if [ -z "$WORKSPACE" ]
then
    pushd $(dirname $BASH_SOURCE)
    export WORKSPACE=$(git rev-parse --show-toplevel)
    echo Assume default WORKSPACE $WORKSPACE
    popd
fi

if [ -z "$TESTS" ]; then
    export TESTS=$WORKSPACE/ruby/integration_tests
    echo Assume default TESTS root $TESTS
fi

if [ ! -d "$TESTS" ]; then
    echo TESTS root directory does not exist.
    exit 1
fi

if [ -z "$CLI" ]; then
    export CLI=$WORKSPACE/ruby/cli
    echo Assume default CLI root $CLI
fi

if [ ! -d "$CLI" ]; then
    echo CLI root directory does not exist.
    exit 1
fi

cd $WORKSPACE

git submodule update --init --recursive

# To avoid messing with update-alternatives
# we set up our own Ruby 1.9 sandbox
mkdir -p $PWD/.ruby/bin
mkdir -p $PWD/.ruby/gems

ln -sf /usr/bin/ruby1.9.3 $PWD/.ruby/bin/ruby
ln -sf /usr/bin/irb1.9.3 $PWD/.ruby/bin/irb
ln -sf /usr/bin/gem1.9.3 $PWD/.ruby/bin/gem

export PATH=$PWD/.ruby/bin:$PWD/.ruby/gems/bin:$PATH
export GEM_HOME=$PWD/.ruby/gems

rm -rf $WORKSPACE/.ruby

gem install bundler --no-ri --no-rdoc

cd $CLI
bundle install --retry 10

cd $TESTS
rm -rf reports/log
mkdir -p reports/log
bundle install --retry 10
