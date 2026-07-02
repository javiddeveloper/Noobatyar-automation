#!/bin/bash
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0b2tlbl90eXBlIjoiYWNjZXNzIiwiZXhwIjoxNzgzNTQ2Mzc2LCJpYXQiOjE3ODI5NDE1NzYsImp0aSI6ImMwMDQzZDRjOWMwMTRiMzQ4NWJhMjExNjI4MGViYzk3IiwidXNlcl9pZCI6IjEifQ.F0sFHNGcoDQDUCoSBvx94TvaVaAzvUtlzVw6yJXD9hM"
echo "Trying to access VIP endpoint (should be forbidden or not found as we haven't checked vip url yet)"
curl -s -X GET http://localhost:8000/api/vip/ -H "Authorization: Bearer $TOKEN"
