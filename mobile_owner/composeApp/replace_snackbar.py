import os
import re

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    modified = False

    # Check if there is a snackbarHostState
    if 'SnackbarHost(' not in content:
        return False

    # Import ToastyHost
    if 'xyz.sattar.javid.proqueue.core.ui.components.ToastyHost' not in content:
        imports = list(re.finditer(r'^import .+$', content, re.MULTILINE))
        if imports:
            last_import = imports[-1]
            content = content[:last_import.end()] + '\nimport xyz.sattar.javid.proqueue.core.ui.components.ToastyHost\n' + content[last_import.end():]
            modified = True

    # 1. Replace complex SnackbarHost(...) { ... }
    # Look for "SnackbarHost(hostState = snackbarHostState)" or "SnackbarHost(snackbarHostState)"
    
    start_idx = 0
    while True:
        idx1 = content.find('SnackbarHost(hostState = snackbarHostState)', start_idx)
        idx2 = content.find('SnackbarHost(snackbarHostState)', start_idx)
        
        matches = [i for i in [idx1, idx2] if i != -1]
        if not matches:
            break
            
        start_idx = min(matches)
        
        # Check if there's a lambda { after it
        # find the closing paren
        paren_close = content.find(')', start_idx)
        if paren_close != -1:
            # check what's after
            after_paren = content[paren_close+1:paren_close+20].strip()
            if after_paren.startswith('{'):
                # it has a trailing lambda block, we need to find its end
                block_start = content.find('{', paren_close)
                brace_count = 0
                block_end = -1
                for i in range(block_start, len(content)):
                    if content[i] == '{':
                        brace_count += 1
                    elif content[i] == '}':
                        brace_count -= 1
                        if brace_count == 0:
                            block_end = i
                            break
                if block_end != -1:
                    content = content[:start_idx] + 'ToastyHost(hostState = snackbarHostState)' + content[block_end+1:]
                    modified = True
            else:
                # no trailing lambda block
                content = content[:start_idx] + 'ToastyHost(hostState = snackbarHostState)' + content[paren_close+1:]
                modified = True
                
        start_idx += 1

    if modified:
        with open(filepath, 'w') as f:
            f.write(content)
        return True
    return False

base_dir = '/Users/javid/Documents/Projects/noobatyar/Noobatyar-automation/mobile_owner/composeApp/src/commonMain/kotlin'
count = 0
for root, dirs, files in os.walk(base_dir):
    for file in files:
        if file.endswith('.kt'):
            if process_file(os.path.join(root, file)):
                count += 1
                print(f"Refactored {file}")

print(f"Total modified: {count}")
