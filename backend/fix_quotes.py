with open("api_curl.md", "r") as f:
    lines = f.readlines()

for i in range(len(lines)):
    if 'Authorization: Bearer <access_token>' in lines[i] and 'Authorization: Bearer <access_token>"' not in lines[i]:
        lines[i] = lines[i].replace('Authorization: Bearer <access_token>', 'Authorization: Bearer <access_token>"')

with open("api_curl.md", "w") as f:
    f.writelines(lines)
