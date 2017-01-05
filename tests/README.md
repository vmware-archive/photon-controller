# Photon Controller - Ruby

## Requirements

### ruby 1.9 or above
- use [RVM](https://rvm.io/) to install ruby
- OSX 10.8+ system ruby should be fine

### bundler

~~~bash
gem install bundler
~~~

## Development workflow

Before commiting changes to any of the code in this folder, you must run rubocop from the root as well as all unit-tests.
You can do that by executing the following commands from this folder:

~~~bash
cd ./ci
./run_ci.sh
~~~
