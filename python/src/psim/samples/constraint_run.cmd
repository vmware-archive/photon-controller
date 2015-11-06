# Configurations
seed 10000
log /tmp/psim.log DEBUG

# Simulator
load_tree ./samples/schedulers.yml
# print_tree
run ./samples/simple_requests.yml
print_stats
