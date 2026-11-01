import { useEffect } from 'react'
import './characters.css'

/**
 * 左侧趣味几何：4 个角色的完整交互，移植自
 * github.com/huangj17/login-animated-characters（仅「黑色」角色配色提亮以适配深蓝底）。
 * 交互：空闲鼠标跟随 / 随机眨眼 / 账号聚焦互视 / 密码聚焦回避 / 密码可见偷瞄 / 登录失败摇头+难过。
 */

let disposed = true
let mouseX = 0
let mouseY = 0
let isTyping = false // 账号聚焦
let isLookingAtEachOther = false
let isPasswordFocused = false
let showPassword = false
let pwdLen = 0
let isLoginError = false
let isPurpleBlinking = false
let isBlackBlinking = false
let isPurplePeeking = false

let typingTimer: ReturnType<typeof setTimeout> | null = null
let blinkPurpleTimer: ReturnType<typeof setTimeout> | null = null
let blinkBlackTimer: ReturnType<typeof setTimeout> | null = null
let peekTimer: ReturnType<typeof setTimeout> | null = null
let errorRecoverTimer: ReturnType<typeof setTimeout> | null = null

const $ = (id: string): HTMLElement => document.getElementById(id) as HTMLElement

function calcPosition(el: HTMLElement) {
  const rect = el.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 3
  const dx = mouseX - cx
  const dy = mouseY - cy
  const faceX = Math.max(-15, Math.min(15, dx / 20))
  const faceY = Math.max(-10, Math.min(10, dy / 30))
  const bodySkew = Math.max(-6, Math.min(6, -dx / 120))
  return { faceX, faceY, bodySkew }
}

function calcPupilOffset(el: HTMLElement, maxDist: number) {
  const rect = el.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2
  const dx = mouseX - cx
  const dy = mouseY - cy
  const dist = Math.min(Math.sqrt(dx * dx + dy * dy), maxDist)
  const angle = Math.atan2(dy, dx)
  return { x: Math.cos(angle) * dist, y: Math.sin(angle) * dist }
}

function updateCharacters() {
  if (disposed) return

  const purple = $('char-purple')
  const black = $('char-black')
  const orange = $('char-orange')
  const yellow = $('char-yellow')

  const purplePos = calcPosition(purple)
  const blackPos = calcPosition(black)
  const orangePos = calcPosition(orange)
  const yellowPos = calcPosition(yellow)

  const isShowingPwd = pwdLen > 0 && showPassword
  const isLookingAway = isPasswordFocused && !showPassword

  // ---- 紫色角色 ----
  if (isShowingPwd) {
    purple.style.transform = 'skewX(0deg)'
    purple.style.height = '370px'
  } else if (isLookingAway) {
    purple.style.transform = 'skewX(-14deg) translateX(-20px)'
    purple.style.height = '410px'
  } else if (isTyping) {
    purple.style.transform = `skewX(${(purplePos.bodySkew || 0) - 12}deg) translateX(40px)`
    purple.style.height = '410px'
  } else {
    purple.style.transform = `skewX(${purplePos.bodySkew}deg)`
    purple.style.height = '370px'
  }

  const purpleEyes = $('purple-eyes')
  const purpleEyeL = $('purple-eye-l')
  const purpleEyeR = $('purple-eye-r')
  const purplePupilL = $('purple-pupil-l')
  const purplePupilR = $('purple-pupil-r')

  purpleEyeL.style.height = isPurpleBlinking ? '2px' : '18px'
  purpleEyeR.style.height = isPurpleBlinking ? '2px' : '18px'

  if (isLoginError) {
    purpleEyes.style.left = '30px'
    purpleEyes.style.top = '55px'
    purplePupilL.style.transform = 'translate(-3px, 4px)'
    purplePupilR.style.transform = 'translate(-3px, 4px)'
  } else if (isLookingAway) {
    purpleEyes.style.left = '20px'
    purpleEyes.style.top = '25px'
    purplePupilL.style.transform = 'translate(-5px, -5px)'
    purplePupilR.style.transform = 'translate(-5px, -5px)'
  } else if (isShowingPwd) {
    purpleEyes.style.left = '20px'
    purpleEyes.style.top = '35px'
    const px = isPurplePeeking ? 4 : -4
    const py = isPurplePeeking ? 5 : -4
    purplePupilL.style.transform = `translate(${px}px, ${py}px)`
    purplePupilR.style.transform = `translate(${px}px, ${py}px)`
  } else if (isLookingAtEachOther) {
    purpleEyes.style.left = '55px'
    purpleEyes.style.top = '65px'
    purplePupilL.style.transform = 'translate(3px, 4px)'
    purplePupilR.style.transform = 'translate(3px, 4px)'
  } else {
    purpleEyes.style.left = `${45 + purplePos.faceX}px`
    purpleEyes.style.top = `${40 + purplePos.faceY}px`
    const po = calcPupilOffset(purpleEyeL, 5)
    purplePupilL.style.transform = `translate(${po.x}px, ${po.y}px)`
    purplePupilR.style.transform = `translate(${po.x}px, ${po.y}px)`
  }

  // ---- 黑色角色 ----
  if (isShowingPwd) {
    black.style.transform = 'skewX(0deg)'
  } else if (isLookingAway) {
    black.style.transform = 'skewX(12deg) translateX(-10px)'
  } else if (isLookingAtEachOther) {
    black.style.transform = `skewX(${(blackPos.bodySkew || 0) * 1.5 + 10}deg) translateX(20px)`
  } else if (isTyping) {
    black.style.transform = `skewX(${(blackPos.bodySkew || 0) * 1.5}deg)`
  } else {
    black.style.transform = `skewX(${blackPos.bodySkew}deg)`
  }

  const blackEyes = $('black-eyes')
  const blackEyeL = $('black-eye-l')
  const blackEyeR = $('black-eye-r')
  const blackPupilL = $('black-pupil-l')
  const blackPupilR = $('black-pupil-r')

  blackEyeL.style.height = isBlackBlinking ? '2px' : '16px'
  blackEyeR.style.height = isBlackBlinking ? '2px' : '16px'

  if (isLoginError) {
    blackEyes.style.left = '15px'
    blackEyes.style.top = '40px'
    blackPupilL.style.transform = 'translate(-3px, 4px)'
    blackPupilR.style.transform = 'translate(-3px, 4px)'
  } else if (isLookingAway) {
    blackEyes.style.left = '10px'
    blackEyes.style.top = '20px'
    blackPupilL.style.transform = 'translate(-4px, -5px)'
    blackPupilR.style.transform = 'translate(-4px, -5px)'
  } else if (isShowingPwd) {
    blackEyes.style.left = '10px'
    blackEyes.style.top = '28px'
    blackPupilL.style.transform = 'translate(-4px, -4px)'
    blackPupilR.style.transform = 'translate(-4px, -4px)'
  } else if (isLookingAtEachOther) {
    blackEyes.style.left = '32px'
    blackEyes.style.top = '12px'
    blackPupilL.style.transform = 'translate(0px, -4px)'
    blackPupilR.style.transform = 'translate(0px, -4px)'
  } else {
    blackEyes.style.left = `${26 + blackPos.faceX}px`
    blackEyes.style.top = `${32 + blackPos.faceY}px`
    const bo = calcPupilOffset(blackEyeL, 4)
    blackPupilL.style.transform = `translate(${bo.x}px, ${bo.y}px)`
    blackPupilR.style.transform = `translate(${bo.x}px, ${bo.y}px)`
  }

  // ---- 橙色角色 ----
  const orangeMouth = $('orange-mouth')
  if (isLoginError) {
    orangeMouth.style.left = `${80 + orangePos.faceX}px`
    orangeMouth.style.top = '130px'
  }
  if (isShowingPwd) {
    orange.style.transform = 'skewX(0deg)'
  } else {
    orange.style.transform = `skewX(${orangePos.bodySkew}deg)`
  }

  const orangeEyes = $('orange-eyes')
  const orangePupilL = $('orange-pupil-l')
  const orangePupilR = $('orange-pupil-r')

  if (isLoginError) {
    orangeEyes.style.left = '60px'
    orangeEyes.style.top = '95px'
    orangePupilL.style.transform = 'translate(-3px, 4px)'
    orangePupilR.style.transform = 'translate(-3px, 4px)'
  } else if (isLookingAway) {
    orangeEyes.style.left = '50px'
    orangeEyes.style.top = '75px'
    orangePupilL.style.transform = 'translate(-5px, -5px)'
    orangePupilR.style.transform = 'translate(-5px, -5px)'
  } else if (isShowingPwd) {
    orangeEyes.style.left = '50px'
    orangeEyes.style.top = '85px'
    orangePupilL.style.transform = 'translate(-5px, -4px)'
    orangePupilR.style.transform = 'translate(-5px, -4px)'
  } else {
    orangeEyes.style.left = `${82 + orangePos.faceX}px`
    orangeEyes.style.top = `${90 + orangePos.faceY}px`
    const oo = calcPupilOffset(orangePupilL, 5)
    orangePupilL.style.transform = `translate(${oo.x}px, ${oo.y}px)`
    orangePupilR.style.transform = `translate(${oo.x}px, ${oo.y}px)`
  }

  // ---- 黄色角色 ----
  if (isShowingPwd) {
    yellow.style.transform = 'skewX(0deg)'
  } else {
    yellow.style.transform = `skewX(${yellowPos.bodySkew}deg)`
  }

  const yellowEyes = $('yellow-eyes')
  const yellowPupilL = $('yellow-pupil-l')
  const yellowPupilR = $('yellow-pupil-r')
  const yellowMouth = $('yellow-mouth')

  if (isLoginError) {
    yellowEyes.style.left = '35px'
    yellowEyes.style.top = '45px'
    yellowPupilL.style.transform = 'translate(-3px, 4px)'
    yellowPupilR.style.transform = 'translate(-3px, 4px)'
    yellowMouth.style.left = '30px'
    yellowMouth.style.top = '92px'
    yellowMouth.style.transform = 'rotate(-8deg)'
  } else if (isLookingAway) {
    yellowEyes.style.left = '20px'
    yellowEyes.style.top = '30px'
    yellowPupilL.style.transform = 'translate(-5px, -5px)'
    yellowPupilR.style.transform = 'translate(-5px, -5px)'
    yellowMouth.style.left = '15px'
    yellowMouth.style.top = '78px'
    yellowMouth.style.transform = 'rotate(0deg)'
  } else if (isShowingPwd) {
    yellowEyes.style.left = '20px'
    yellowEyes.style.top = '35px'
    yellowPupilL.style.transform = 'translate(-5px, -4px)'
    yellowPupilR.style.transform = 'translate(-5px, -4px)'
    yellowMouth.style.left = '10px'
    yellowMouth.style.top = '88px'
    yellowMouth.style.transform = 'rotate(0deg)'
  } else {
    yellowEyes.style.left = `${52 + yellowPos.faceX}px`
    yellowEyes.style.top = `${40 + yellowPos.faceY}px`
    const yo = calcPupilOffset(yellowPupilL, 5)
    yellowPupilL.style.transform = `translate(${yo.x}px, ${yo.y}px)`
    yellowPupilR.style.transform = `translate(${yo.x}px, ${yo.y}px)`
    yellowMouth.style.left = `${40 + yellowPos.faceX}px`
    yellowMouth.style.top = `${88 + yellowPos.faceY}px`
    yellowMouth.style.transform = 'rotate(0deg)'
  }
}

function scheduleBlinkPurple() {
  if (disposed) return
  blinkPurpleTimer = setTimeout(() => {
    isPurpleBlinking = true
    updateCharacters()
    setTimeout(() => {
      isPurpleBlinking = false
      updateCharacters()
      scheduleBlinkPurple()
    }, 150)
  }, Math.random() * 4000 + 3000)
}

function scheduleBlinkBlack() {
  if (disposed) return
  blinkBlackTimer = setTimeout(() => {
    isBlackBlinking = true
    updateCharacters()
    setTimeout(() => {
      isBlackBlinking = false
      updateCharacters()
      scheduleBlinkBlack()
    }, 150)
  }, Math.random() * 4000 + 3000)
}

function schedulePeek() {
  if (disposed) return
  if (pwdLen > 0 && showPassword) {
    peekTimer = setTimeout(() => {
      if (pwdLen > 0 && showPassword) {
        isPurplePeeking = true
        updateCharacters()
        setTimeout(() => {
          isPurplePeeking = false
          updateCharacters()
          schedulePeek()
        }, 800)
      }
    }, Math.random() * 3000 + 2000)
  }
}

function setTyping(typing: boolean) {
  isTyping = typing
  if (typing) {
    isLookingAtEachOther = true
    if (typingTimer) clearTimeout(typingTimer)
    typingTimer = setTimeout(() => {
      isLookingAtEachOther = false
      updateCharacters()
    }, 800)
  } else {
    isLookingAtEachOther = false
  }
  updateCharacters()
}

function onMousemove(e: MouseEvent) {
  mouseX = e.clientX
  mouseY = e.clientY
  if (!isTyping && !isLoginError) updateCharacters()
}

function initCharacters() {
  disposed = false
  document.addEventListener('mousemove', onMousemove)
  scheduleBlinkPurple()
  scheduleBlinkBlack()
  updateCharacters()
}

function destroyCharacters() {
  disposed = true
  document.removeEventListener('mousemove', onMousemove)
  if (typingTimer) clearTimeout(typingTimer)
  if (blinkPurpleTimer) clearTimeout(blinkPurpleTimer)
  if (blinkBlackTimer) clearTimeout(blinkBlackTimer)
  if (peekTimer) clearTimeout(peekTimer)
  if (errorRecoverTimer) clearTimeout(errorRecoverTimer)
  typingTimer = blinkPurpleTimer = blinkBlackTimer = peekTimer = errorRecoverTimer = null
  isTyping = false
  isLookingAtEachOther = false
  isPasswordFocused = false
  showPassword = false
  pwdLen = 0
  isLoginError = false
  isPurpleBlinking = false
  isBlackBlinking = false
  isPurplePeeking = false
}

/** 登录页表单状态 → 角色交互信号。 */
export type CharacterSignals = {
  accountFocused?: boolean
  passwordFocused?: boolean
  passwordVisible?: boolean
  passwordLen?: number
}

export function signalCharacters(patch: CharacterSignals) {
  if (patch.accountFocused !== undefined) setTyping(patch.accountFocused)
  if (patch.passwordFocused !== undefined) {
    isPasswordFocused = patch.passwordFocused
    updateCharacters()
  }
  if (patch.passwordVisible !== undefined) {
    showPassword = patch.passwordVisible
    if (showPassword) schedulePeek()
    updateCharacters()
  }
  if (patch.passwordLen !== undefined) {
    pwdLen = patch.passwordLen
    updateCharacters()
  }
}

export function triggerLoginError() {
  if (disposed) return
  if (errorRecoverTimer) {
    clearTimeout(errorRecoverTimer)
    errorRecoverTimer = null
  }

  const shakeIds = ['purple-eyes', 'black-eyes', 'orange-eyes', 'yellow-eyes', 'yellow-mouth', 'orange-mouth']
  const shakeEls = shakeIds.map($)
  shakeEls.forEach((el) => el.classList.remove('shake-head'))
  void document.body.offsetHeight // 强制回流，重复触发也能重播动画

  isLoginError = true
  isPasswordFocused = false
  updateCharacters()
  $('orange-mouth').classList.add('visible')

  setTimeout(() => shakeEls.forEach((el) => el.classList.add('shake-head')), 350)

  errorRecoverTimer = setTimeout(() => {
    isLoginError = false
    errorRecoverTimer = null
    $('orange-mouth').classList.remove('visible')
    shakeEls.forEach((el) => el.classList.remove('shake-head'))
    updateCharacters()
  }, 2500)
}

export default function AnimatedCharacters() {
  useEffect(() => {
    initCharacters()
    return destroyCharacters
  }, [])

  return (
    <div className="characters-scene" aria-hidden>
      {/* 紫色角色 */}
      <div className="character char-purple" id="char-purple">
        <div className="eyes" id="purple-eyes" style={{ left: 45, top: 40, gap: 28 }}>
          <div className="eyeball" id="purple-eye-l" style={{ width: 18, height: 18 }}>
            <div className="pupil" id="purple-pupil-l" style={{ width: 7, height: 7 }} />
          </div>
          <div className="eyeball" id="purple-eye-r" style={{ width: 18, height: 18 }}>
            <div className="pupil" id="purple-pupil-r" style={{ width: 7, height: 7 }} />
          </div>
        </div>
      </div>
      {/* 黑色角色 */}
      <div className="character char-black" id="char-black">
        <div className="eyes" id="black-eyes" style={{ left: 26, top: 32, gap: 20 }}>
          <div className="eyeball" id="black-eye-l" style={{ width: 16, height: 16 }}>
            <div className="pupil" id="black-pupil-l" style={{ width: 6, height: 6 }} />
          </div>
          <div className="eyeball" id="black-eye-r" style={{ width: 16, height: 16 }}>
            <div className="pupil" id="black-pupil-r" style={{ width: 6, height: 6 }} />
          </div>
        </div>
      </div>
      {/* 橙色角色 */}
      <div className="character char-orange" id="char-orange">
        <div className="eyes" id="orange-eyes" style={{ left: 82, top: 90, gap: 28 }}>
          <div className="bare-pupil" id="orange-pupil-l" />
          <div className="bare-pupil" id="orange-pupil-r" />
        </div>
        <div className="orange-mouth" id="orange-mouth" style={{ left: 90, top: 120 }} />
      </div>
      {/* 黄色角色 */}
      <div className="character char-yellow" id="char-yellow">
        <div className="eyes" id="yellow-eyes" style={{ left: 52, top: 40, gap: 20 }}>
          <div className="bare-pupil" id="yellow-pupil-l" />
          <div className="bare-pupil" id="yellow-pupil-r" />
        </div>
        <div className="yellow-mouth" id="yellow-mouth" style={{ left: 40, top: 88 }} />
      </div>
    </div>
  )
}
