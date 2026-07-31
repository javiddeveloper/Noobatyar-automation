import os
import glob
import re

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    if 'SnackbarHost(hostState = snackbarHostState)' not in content and 'SnackbarHost(snackbarHostState)' not in content:
        return False

    # Import ToastyHost
    if 'xyz.sattar.javid.proqueue.core.ui.components.ToastyHost' not in content:
        import_stmt = "import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost\n"
        # Find last import
        imports = list(re.finditer(r'^import .+$', content, re.MULTILINE))
        if imports:
            last_import = imports[-1]
            content = content[:last_import.end()] + '\n' + import_stmt + content[last_import.end():]

    # Remove the snackbarHost parameter from Scaffold
    # We will use regex to find snackbarHost = { ... }
    # This regex is a bit tricky because of nested braces, so let's do a manual brace counting
    
    start_idx = content.find('snackbarHost = {')
    if start_idx == -1:
        start_idx = content.find('snackbarHost={')
        
    if start_idx != -1:
        brace_count = 0
        in_braces = False
        end_idx = -1
        for i in range(start_idx, len(content)):
            if content[i] == '{':
                brace_count += 1
                in_braces = True
            elif content[i] == '}':
                brace_count -= 1
                if in_braces and brace_count == 0:
                    end_idx = i
                    break
        
        if end_idx != -1:
            # check if there's a comma after
            comma_idx = content.find(',', end_idx)
            if comma_idx != -1 and content[end_idx+1:comma_idx].strip() == '':
                end_idx = comma_idx
            content = content[:start_idx] + content[end_idx+1:]

    # Now we need to wrap the Scaffold in a Box and append ToastyHost
    # Find Scaffold(
    scaffold_start = content.find('Scaffold(')
    if scaffold_start != -1:
        # Find the indentation of Scaffold
        indent_match = re.search(r'([ \t]*)Scaffold\($', content[:scaffold_start+9], re.MULTILINE)
        indent = indent_match.group(1) if indent_match else ''
        
        brace_count = 0
        paren_count = 0
        in_parens = False
        in_braces = False
        scaffold_end = -1
        
        for i in range(scaffold_start, len(content)):
            if content[i] == '(':
                paren_count += 1
                in_parens = True
            elif content[i] == ')':
                paren_count -= 1
            elif content[i] == '{':
                brace_count += 1
                in_braces = True
            elif content[i] == '}':
                brace_count -= 1
                
            if in_parens and paren_count == 0:
                # wait to see if there's a trailing lambda
                next_char_idx = i + 1
                while next_char_idx < len(content) and content[next_char_idx].isspace():
                    next_char_idx += 1
                if next_char_idx < len(content) and content[next_char_idx] != '{':
                    scaffold_end = i
                    break
            
            if in_braces and brace_count == 0 and paren_count == 0:
                scaffold_end = i
                break
                
        if scaffold_end != -1:
            # Wrap in Box
            prefix = f"{indent}androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {{\n"
            suffix = f"\n{indent}    ToastyHost(hostState = snackbarHostState, modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopCenter))\n{indent}}}"
            
            content = content[:scaffold_start] + prefix + content[scaffold_start:scaffold_end+1] + suffix + content[scaffold_end+1:]

    with open(filepath, 'w') as f:
        f.write(content)
        
    return True

base_dir = '/Users/javid/Documents/Projects/noobatyar/Noobatyar-automation/mobile_owner/composeApp/src/commonMain/kotlin'
count = 0
for root, dirs, files in os.walk(base_dir):
    for file in files:
        if file.endswith('.kt'):
            if process_file(os.path.join(root, file)):
                count += 1
                print(f"Refactored {file}")

print(f"Total modified: {count}")
