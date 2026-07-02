#!/bin/bash
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0b2tlbl90eXBlIjoiYWNjZXNzIiwiZXhwIjoxNzgzNTQ2Mzc2LCJpYXQiOjE3ODI5NDE1NzYsImp0aSI6ImMwMDQzZDRjOWMwMTRiMzQ4NWJhMjExNjI4MGViYzk3IiwidXNlcl9pZCI6IjEifQ.F0sFHNGcoDQDUCoSBvx94TvaVaAzvUtlzVw6yJXD9hM"
echo "Creating business..."
curl -s -X POST http://localhost:8000/api/business/ \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "My Test Salon",
    "category": "BEAUTY_SALON",
    "phone": "09123456789",
    "address": "Tehran",
    "default_service_duration": 30,
    "work_start_hour": 9,
    "work_end_hour": 18
  }'
