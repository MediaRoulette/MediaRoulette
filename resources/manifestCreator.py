#!/usr/bin/env python3
"""
Manifest Creator for MediaRoulette External Resources

Generates a manifest.json file from the resources folder, tracking:
- File paths (relative to resources/)
- File sizes
- SHA256 hashes for integrity verification
- Timestamps

Usage:
    python manifestCreator.py [resources_dir] [output_file]
    
    Default resources_dir: ./src/main/resources
    Default output_file: ./resources/manifest.json (for GitHub)
"""

import os
import sys
import json
import hashlib
from datetime import datetime
from pathlib import Path

# Folders to include in the manifest
RESOURCE_FOLDERS = ['images', 'fonts', 'config', 'locales', 'data']

# File extensions to include
INCLUDE_EXTENSIONS = {
    '.jpg', '.jpeg', '.png', '.gif', '.webp', '.svg',  # Images
    '.ttf', '.otf', '.woff', '.woff2',                  # Fonts
    '.json', '.yaml', '.yml',                           # Config
    '.properties',                                       # Locales
    '.txt', '.csv'                                       # Data
}

# Files to explicitly include (even if extension not in list)
INCLUDE_FILES = {'subreddits.txt', 'basic_dictionary.txt', 'themes.json'}

# Files/folders to exclude
EXCLUDE_PATTERNS = {'__pycache__', '.DS_Store', 'Thumbs.db', '.git'}


def calculate_sha256(file_path: Path) -> str:
    """Calculate SHA256 hash of a file."""
    sha256 = hashlib.sha256()
    with open(file_path, 'rb') as f:
        while chunk := f.read(8192):
            sha256.update(chunk)
    return sha256.hexdigest()


def should_include(file_path: Path, relative_path: str) -> bool:
    """Determine if a file should be included in the manifest."""
    filename = file_path.name
    
    # Check exclusions
    for pattern in EXCLUDE_PATTERNS:
        if pattern in str(file_path):
            return False
    
    # Check if in allowed folders
    parts = relative_path.split('/')
    if len(parts) > 1 and parts[0] not in RESOURCE_FOLDERS:
        # Root level files
        if filename in INCLUDE_FILES:
            return True
        return False
    
    # Check extension or explicit include
    if filename in INCLUDE_FILES:
        return True
    
    return file_path.suffix.lower() in INCLUDE_EXTENSIONS


def get_file_category(relative_path: str) -> str:
    """Categorize a file based on its path."""
    if relative_path.startswith('images/'):
        return 'image'
    elif relative_path.startswith('fonts/'):
        return 'font'
    elif relative_path.startswith('config/'):
        return 'config'
    elif relative_path.startswith('locales/'):
        return 'locale'
    elif relative_path.startswith('data/'):
        return 'data'
    else:
        return 'other'


def is_required_file(relative_path: str) -> bool:
    """Determine if a file is required for core functionality."""
    required_patterns = [
        'config/themes.json',
        'locales/messages',
        'fonts/',  # At least one font is needed
    ]
    for pattern in required_patterns:
        if pattern in relative_path:
            return True
    return False


def create_manifest(resources_dir: Path, output_file: Path) -> dict:
    """Create the manifest from the resources directory."""
    
    if not resources_dir.exists():
        print(f"Error: Resources directory not found: {resources_dir}")
        sys.exit(1)
    
    manifest = {
        "version": "1.0.0",
        "lastUpdated": int(datetime.now().timestamp() * 1000),
        "generatedAt": datetime.now().isoformat(),
        "baseUrl": "https://raw.githubusercontent.com/MediaRoulette/MediaRoulette/main/resources/",
        "resources": {}
    }
    
    total_size = 0
    file_count = 0
    categories = {}
    
    print(f"\n🔍 Scanning: {resources_dir}")
    print("─" * 50)
    
    # Walk through all files
    for root, dirs, files in os.walk(resources_dir):
        # Skip excluded directories
        dirs[:] = [d for d in dirs if d not in EXCLUDE_PATTERNS]
        
        for filename in sorted(files):
            file_path = Path(root) / filename
            relative_path = file_path.relative_to(resources_dir).as_posix()
            
            if not should_include(file_path, relative_path):
                continue
            
            # Calculate file info
            file_size = file_path.stat().st_size
            file_hash = calculate_sha256(file_path)
            category = get_file_category(relative_path)
            required = is_required_file(relative_path)
            
            # Add to manifest
            manifest["resources"][relative_path] = {
                "sha256": file_hash,
                "size": file_size,
                "required": required
            }
            
            # Track stats
            total_size += file_size
            file_count += 1
            categories[category] = categories.get(category, 0) + 1
            
            # Print progress
            size_str = format_size(file_size)
            req_marker = "★" if required else "○"
            print(f"  {req_marker} {relative_path:<45} {size_str:>10}")
    
    # Add summary to manifest
    manifest["summary"] = {
        "totalFiles": file_count,
        "totalSize": total_size,
        "categories": categories
    }
    
    # Create output directory if needed
    output_file.parent.mkdir(parents=True, exist_ok=True)
    
    # Write manifest
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    
    print("─" * 50)
    print(f"\n✅ Manifest created: {output_file}")
    print(f"   📁 Files: {file_count}")
    print(f"   📦 Total size: {format_size(total_size)}")
    print(f"   📂 Categories: {categories}")
    print()
    
    return manifest


def format_size(size_bytes: int) -> str:
    """Format bytes into human-readable size."""
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    elif size_bytes < 1024 * 1024 * 1024:
        return f"{size_bytes / (1024 * 1024):.2f} MB"
    else:
        return f"{size_bytes / (1024 * 1024 * 1024):.2f} GB"


def main():
    # Default paths - resources folder is at project root
    script_dir = Path(__file__).parent
    default_resources = script_dir  # manifestCreator.py is now inside resources/
    default_output = script_dir / "manifest.json"
    
    # Parse arguments
    resources_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else default_resources
    output_file = Path(sys.argv[2]) if len(sys.argv) > 2 else default_output
    
    print()
    print("╔════════════════════════════════════════════════════╗")
    print("║     📋 MediaRoulette Manifest Creator              ║")
    print("╚════════════════════════════════════════════════════╝")
    
    create_manifest(resources_dir, output_file)


if __name__ == "__main__":
    main()
