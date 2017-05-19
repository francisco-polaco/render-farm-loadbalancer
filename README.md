# Compile
```
mvn compile
```

# Execute
```
mvn exec:java [-Dexec.args="-p PORT"]
```

Default port is 8000

# Triggering Auto Scaler
```
python3 generate_requests.py loadbalancer_ip
```


