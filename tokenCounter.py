import os
import re
import tiktoken
import argparse
from pathlib import Path


def num_tokens_from_string(string, encoding_name="cl100k_base"):
    """Returns the number of tokens in a text string."""
    encoding = tiktoken.get_encoding(encoding_name)
    num_tokens = len(encoding.encode(string))
    return num_tokens


def should_ignore_file(file_path, ignore_patterns):
    """Check if file should be ignored based on patterns."""
    for pattern in ignore_patterns:
        if re.search(pattern, str(file_path)):
            return True
    return False


def should_ignore_dir(dir_path, ignore_dirs):
    """Check if directory should be ignored."""
    for ignore_dir in ignore_dirs:
        if ignore_dir in str(dir_path):
            return True
    return False


def count_tokens_in_repo(repo_path, encoding_name="cl100k_base",
                         ignore_dirs=None, ignore_patterns=None,
                         file_extensions=None, max_file_size_mb=10):
    """
    Count tokens in all text files in a repository.

    Args:
        repo_path: Path to the repository
        encoding_name: The encoding to use for tokenization
        ignore_dirs: List of directory names to ignore (e.g., ['.git', 'node_modules'])
        ignore_patterns: List of regex patterns to ignore files
        file_extensions: List of file extensions to include (if None, includes all text files)
        max_file_size_mb: Maximum file size to process in MB

    Returns:
        dict: Statistics about tokens in the repository
    """
    if ignore_dirs is None:
        ignore_dirs = ['.git', 'node_modules', 'venv', '.venv', 'env', '.env',
                       '__pycache__', 'build', 'dist', '.idea', '.vscode']

    if ignore_patterns is None:
        ignore_patterns = [r'\.min\.js$', r'\.min\.css$', r'\.map$', r'package-lock\.json$']

    max_file_size = max_file_size_mb * 1024 * 1024  # Convert to bytes

    repo_path = Path(repo_path)
    total_tokens = 0
    file_count = 0
    skipped_files = 0
    large_files = 0
    binary_files = 0

    files_with_tokens = []

    for root, dirs, files in os.walk(repo_path):
        # Filter out directories we want to ignore
        dirs[:] = [d for d in dirs if not should_ignore_dir(os.path.join(root, d), ignore_dirs)]

        for file in files:
            file_path = Path(os.path.join(root, file))

            # Skip files based on patterns
            if should_ignore_file(file_path, ignore_patterns):
                skipped_files += 1
                continue

            # Filter by extension if specified
            if file_extensions and not any(file.endswith(ext) for ext in file_extensions):
                skipped_files += 1
                continue

            # Check file size
            try:
                file_size = os.path.getsize(file_path)
                if file_size > max_file_size:
                    large_files += 1
                    continue

                # Try to read the file as text
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        content = f.read()

                    # Count tokens
                    tokens = num_tokens_from_string(content, encoding_name)
                    total_tokens += tokens
                    file_count += 1

                    rel_path = str(file_path.relative_to(repo_path))
                    files_with_tokens.append((rel_path, tokens, file_size))

                except UnicodeDecodeError:
                    # Likely a binary file
                    binary_files += 1
                    continue

            except Exception as e:
                print(f"Error processing {file_path}: {e}")
                skipped_files += 1

    # Sort files by token count (descending)
    files_with_tokens.sort(key=lambda x: x[1], reverse=True)

    return {
        "total_tokens": total_tokens,
        "file_count": file_count,
        "skipped_files": skipped_files,
        "large_files": large_files,
        "binary_files": binary_files,
        "files_with_tokens": files_with_tokens
    }


def format_size(size_bytes):
    """Format file size in human readable format."""
    for unit in ['B', 'KB', 'MB']:
        if size_bytes < 1024.0:
            return f"{size_bytes:.2f} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.2f} GB"


def main():
    parser = argparse.ArgumentParser(description='Count tokens in a repository for AI context limits')
    parser.add_argument('repo_path', help='Path to the repository')
    parser.add_argument('--encoding', default='cl100k_base',
                        help='Tokenizer encoding (default: cl100k_base for GPT-4/Claude)')
    parser.add_argument('--max-file-size', type=int, default=10, help='Maximum file size in MB to process')
    parser.add_argument('--extensions', help='Comma-separated list of file extensions to include')
    parser.add_argument('--top', type=int, default=20, help='Number of top token-heavy files to display')
    parser.add_argument('--verbose', action='store_true', help='Show detailed output')

    args = parser.parse_args()

    # Convert extensions string to list if provided
    file_extensions = args.extensions.split(',') if args.extensions else None

    result = count_tokens_in_repo(
        args.repo_path,
        encoding_name=args.encoding,
        max_file_size_mb=args.max_file_size,
        file_extensions=file_extensions
    )

    # Print summary
    print(f"\n{'=' * 60}")
    print(f"Repository Token Analysis: {args.repo_path}")
    print(f"{'=' * 60}")
    print(f"Total tokens: {result['total_tokens']:,}")
    print(f"Files processed: {result['file_count']}")
    print(f"Files skipped: {result['skipped_files']}")
    print(f"Large files (>{args.max_file_size}MB): {result['large_files']}")
    print(f"Binary files: {result['binary_files']}")

    # Context window estimates for different models
    print(f"\n{'=' * 60}")
    print(f"Context Window Usage Estimates")
    print(f"{'=' * 60}")

    models = {
        "GPT-3.5 Turbo": 16_385,
        "GPT-4 Turbo": 128_000,
        "Claude 3 Opus": 200_000,
        "Claude 3 Sonnet": 180_000,
        "Claude 3 Haiku": 48_000
    }

    for model, context_limit in models.items():
        percentage = (result['total_tokens'] / context_limit) * 100
        status = "✅ Fits" if percentage <= 100 else "❌ Exceeds"
        print(f"{model}: {percentage:.2f}% of {context_limit:,} tokens ({status})")

    # Print top token-heavy files
    print(f"\n{'=' * 60}")
    print(f"Top {min(args.top, len(result['files_with_tokens']))} Token-Heavy Files")
    print(f"{'=' * 60}")
    print(f"{'File Path':<50} {'Tokens':<12} {'Size':<10} {'Tokens/KB':<10}")
    print(f"{'-' * 50} {'-' * 12} {'-' * 10} {'-' * 10}")

    for i, (file_path, tokens, file_size) in enumerate(result['files_with_tokens'][:args.top]):
        tokens_per_kb = tokens / (file_size / 1024) if file_size > 0 else 0
        print(f"{file_path:<50} {tokens:<12,} {format_size(file_size):<10} {tokens_per_kb:.1f}")

    if args.verbose and len(result['files_with_tokens']) > args.top:
        print(f"\n... and {len(result['files_with_tokens']) - args.top} more files")


if __name__ == "__main__":
    main()