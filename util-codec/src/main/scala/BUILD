scala_library(name='scala',
  dependencies=[
    pants('3rdparty:commons-codec'),
    pants('util/util-core/src/main/scala')
  ],
  sources=rglobs('*.scala'),
  provides=artifact(org='com.twitter',
    name='util-codec',
    repo='pants-support/ivy:internal',),
)
