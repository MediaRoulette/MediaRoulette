import os
import sys

def parse_properties(file_path):
    properties = {}
    with open(file_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                properties[key.strip()] = value.strip()
    return properties

def sync_locales(locale_dir):
    en_file = os.path.join(locale_dir, 'messages_en_US.properties')
    if not os.path.exists(en_file):
        print(f"Error: {en_file} not found.")
        return

    en_props = parse_properties(en_file)
    
    files = [f for f in os.listdir(locale_dir) if f.endswith('.properties') and f != 'messages_en_US.properties']
    
    for filename in files:
        file_path = os.path.join(locale_dir, filename)
        target_props = parse_properties(file_path)
        
        missing_keys = []
        for key, value in en_props.items():
            if key not in target_props:
                missing_keys.append((key, value))
        
        if missing_keys:
            print(f"Adding {len(missing_keys)} missing keys to {filename}...")
            with open(file_path, 'a', encoding='utf-8') as f:
                f.write("\n# Missing keys synced from en_US\n")
                for key, value in missing_keys:
                    f.write(f"{key}={value}\n")
                    print(f"  + {key}")
        else:
            print(f"{filename} is up to date.")

if __name__ == "__main__":
    locale_dir = sys.argv[1] if len(sys.argv) > 1 else 'd:/IdeaProjects/MediaRoulette/resources/locales'
    sync_locales(locale_dir)
