#!/bin/bash
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0b2tlbl90eXBlIjoiYWNjZXNzIiwiZXhwIjoxNzgzNTQ2Mzc2LCJpYXQiOjE3ODI5NDE1NzYsImp0aSI6ImMwMDQzZDRjOWMwMTRiMzQ4NWJhMjExNjI4MGViYzk3IiwidXNlcl9pZCI6IjEifQ.F0sFHNGcoDQDUCoSBvx94TvaVaAzvUtlzVw6yJXD9hM"
echo "Listing businesses..."
curl -s -X GET http://localhost:8000/api/business/ \
  -H "Authorization: Bearer $TOKEN"
