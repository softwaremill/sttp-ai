# -*- coding: utf-8 -*-
#
# sttp-ai documentation build configuration file.

# https://about.readthedocs.com/blog/2024/07/addons-by-default/
import os

# Define the canonical URL if you are using a custom domain on Read the Docs
html_baseurl = os.environ.get(
    "READTHEDOCS_CANONICAL_URL",
    "https://sttp-ai.softwaremill.com/",
)

# Tell Jinja2 templates the build is running on Read the Docs
if os.environ.get("READTHEDOCS", "") == "True":
    if "html_context" not in globals():
        html_context = {}
    html_context["READTHEDOCS"] = True

# -- General configuration ------------------------------------------------

extensions = ['myst_parser', 'sphinx_rtd_theme', 'sphinxcontrib.mermaid', 'sphinx_llms_txt']

myst_enable_extensions = ['attrs_block']

llms_txt_title = "sttp-ai"
llms_txt_summary = "Scala client for OpenAI, Claude (Anthropic), and OpenAI-compatible APIs"
llms_txt_full_file = True

# The suffix(es) of source filenames.
source_suffix = {
    '.rst': 'restructuredtext',
    '.md': 'markdown',
}

# The master toctree document.
master_doc = 'index'

# General information about the project.
project = u'sttp-ai'
copyright = u'2026, SoftwareMill'
author = u'SoftwareMill'

# The short X.Y version.
version = u'0.5'
# The full version, including alpha/beta/rc tags.
release = u'0.5'

language = 'en'

# List of patterns, relative to source directory, that match files and
# directories to ignore when looking for source files.
exclude_patterns = [
    '_build', 'Thumbs.db', '.DS_Store',
    '.venv', 'venv', 'env',
    '**/site-packages/**',
    '**/node_modules/**',
    '_templates',
    'requirements.txt',
    'README.md',
    'includes/*',
    'adr',
    'plans',
    'superpowers',
]

pygments_style = 'default'

# sbt build files are Scala; Pygments has no dedicated "sbt" lexer, so code
# fences tagged ```sbt (as in quickstart.md) would otherwise emit
# "Pygments lexer name 'sbt' is not known" warnings. Alias it to the Scala
# lexer instead of changing the ported fence language.
from pygments.lexers.jvm import ScalaLexer
from sphinx.highlighting import lexers
lexers['sbt'] = ScalaLexer()

# -- Options for HTML output ----------------------------------------------

html_theme = 'sphinx_rtd_theme'

htmlhelp_basename = 'sttpaidoc'

highlight_language = 'scala'

# configure edit on github: https://docs.readthedocs.io/en/latest/guides/vcs.html
html_context = {
    'display_github': True,
    'github_user': 'softwaremill',
    'github_repo': 'sttp-ai',
    'github_version': 'master',
    'conf_py_path': '/docs/',
}
