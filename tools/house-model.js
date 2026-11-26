// Generuje app/src/main/assets/house.bin – chmurę punktów domku.
// Uruchomienie: node tools/house-model.js
// Format: "DOMH", int32 liczba punktów, potem dla każdego punktu 7 x float32:
// x, y, z, rodzaj (0 krawędź, 1 okno, 2 pył), pokój, faza, jasność. Little-endian.
// Punkty są przetasowane, więc pierwsza połowa to równomierna próbka (tryb oszczędny).
const fs = require('fs');
const path = require('path');
const P=[],A=[],K=0.84,BT=3.84,BB=2.75,PL=0.45,NR=12;
    const J=()=>(Math.random()-.5)*0.06;
    function pt(x,y,z,k,r,b){P.push(x,y,z);A.push(k,r,Math.random(),b);}
    function L(a,b,o){o=o||{};const d=o.d||50,r=o.r||0,br=o.b||1;const n=Math.ceil(Math.hypot(b[0]-a[0],b[1]-a[1],b[2]-a[2])*d);for(let i=0;i<n;i++){const t=Math.random();pt(a[0]+(b[0]-a[0])*t+J(),a[1]+(b[1]-a[1])*t+J(),a[2]+(b[2]-a[2])*t+J(),0,r,br*(0.7+Math.random()*0.3));}}
    function poly(p,o,open){for(let i=0;i<p.length-(open?1:0);i++)L(p[i],p[(i+1)%p.length],o);}
    function fill(c,u,v,r){const n=Math.hypot(...u)*Math.hypot(...v)*75;for(let i=0;i<n;i++){const s=Math.random(),t=Math.random();pt(c[0]+u[0]*s+v[0]*t,c[1]+u[1]*s+v[1]*t,c[2]+u[2]*s+v[2]*t,1,r,1);}}
    function win(c,u,v,r,mid){const q=[c,[c[0]+u[0],c[1]+u[1],c[2]+u[2]],[c[0]+u[0]+v[0],c[1]+u[1]+v[1],c[2]+u[2]+v[2]],[c[0]+v[0],c[1]+v[1],c[2]+v[2]]];poly(q,{r:r,d:60});if(mid){const m=[c[0]+u[0]/2,c[1]+u[1]/2,c[2]+u[2]/2];L(m,[m[0]+v[0],m[1]+v[1],m[2]+v[2]],{r:r,b:.8});}fill(c,u,v,r);}
    const wZ=(x0,x1,y0,y1,z,r,m)=>win([x0,y0,z],[x1-x0,0,0],[0,y1-y0,0],r,m);
    const wX=(z0,z1,y0,y1,x,r,m)=>win([x,y0,z0],[0,0,z1-z0],[0,y1-y0,0],r,m);
    const R=[[0,14.3,0,11.45],[0,10.4,-2,11.45]];
    const inR=(r,x,z)=>x>=r[0]&&x<=r[1]&&z>=r[2]&&z<=r[3];
    function hd(x,z){let best=-1;for(const r of R){if(!inR(r,x,z))continue;const m=Math.min(x-r[0],r[1]-x,z-r[2],r[3]-z);if(m>best)best=m;}return best<0?null:best;}
    const rh=(x,z)=>{const d=hd(x,z);return d===null?null:BT+d*K;};
    function mkD(o){o.hw=o.w/2;o.yt=o.yb+o.wh;o.yp=o.yt+o.pk;o.yE=o.yt-.15;
      o.m=o.f==='front'?(u,y,d)=>[o.c+u,y,o.at+d]:(u,y,d)=>[o.at-d,y,o.c+u];
      o.loc=o.f==='front'?(x,z)=>[x-o.c,z-o.at]:(x,z)=>[z-o.c,o.at-x];
      o.reach=(u,y)=>{let ph=-1;for(let d=0;d<12;d+=.04){const p=o.m(u,0,d),h=rh(p[0],p[2]);if(h===null)continue;if(h>=y||h<ph-1e-6)return d;ph=h;}return 12;};
      o.dR=o.reach(0,o.yp+.15);o.dEl=o.reach(-o.hw-o.eo,o.yE);o.dEr=o.reach(o.hw+o.eo,o.yE);return o;}
    const yb2=BT+.3*K;
    const D=[mkD({f:'front',c:8.4,at:-.2,w:3.8,eo:.7,yb:BT,wh:2.5,pk:2.15,ov:.8,r:3,wy:[BT+.05,BT+2.3],ww:2.2,base:0}),
      mkD({f:'right',c:5.725,at:14.0,w:3.4,eo:.6,yb:yb2,wh:2.5,pk:1.75,ov:.65,r:4,wy:[yb2+1.15,yb2+2.35],ww:1.8,base:1.0})];
    function hidden(x,z,h){for(const o of D){const l=o.loc(x,z),au=Math.abs(l[0]);if(au<o.hw+o.eo+.1&&l[1]>-.05&&l[1]<o.dR+.1){const dh=o.yE+(o.yp+.15-o.yE)*(1-au/(o.hw+o.eo));if(h<dh+.05)return true;}}return false;}
    const E=.04;
    for(let i=0;i<220000;i++){const x=Math.random()*14.3,z=-2+Math.random()*13.45;if(x>6.4&&x<10.4&&z<-.2)continue;const d=hd(x,z);if(d===null)continue;const h=BT+d*K;if(hidden(x,z,h))continue;
      let b=0;if(d<.03)b=1;else{const a1=hd(x+E,z),a2=hd(x-E,z),c1=hd(x,z+E),c2=hd(x,z-E);if(a1!==null&&a2!==null&&c1!==null&&c2!==null&&(Math.abs(a1+a2-2*d)>.015||Math.abs(c1+c2-2*d)>.015))b=1;else if(d>.35&&(d%.6)<.025)b=.28;}
      if(b)pt(x+J()*.2,h,z+J()*.2,0,0,b*(0.75+Math.random()*.25));}
    for(let i=0;i<80;i++){const z=-2+1.8*Math.random();pt(6.4,rh(6.4,z),z,0,0,1);}
    const out=[[0,-2],[10.4,-2],[10.4,0],[14.3,0],[14.3,11.45],[0,11.45]];
    [BT,BB].forEach(y=>poly(out.map(v=>[v[0],y,v[1]]),{d:55}));out.forEach(v=>L([v[0],BB,v[1]],[v[0],BT,v[1]]));
    poly([[0,PL,-2],[6.5,PL,-2],[6.5,PL,0],[14.3,PL,0],[14.3,PL,11.45],[0,PL,11.45]],{d:60,b:1});
    L([6.5,BB,0],[10.4,BB,0],{b:.6});L([6.5,PL,0],[6.5,BB,0],{b:.7});
    [0,.85,6.5].forEach(x=>L([x,PL,-2],[x,BB,-2],{b:.85}));
    L([14.3,PL,11.45],[14.3,BB,11.45]);L([0,PL,11.45],[0,BB,11.45]);
    [6.9,9.75].forEach(x=>{for(let y=PL;y<BB;y+=.1)pt(x+J()*.5,y,-2.12,0,0,.8);});
    [4.95,8.65].forEach(z=>{for(let y=PL;y<BB;y+=.1)pt(14.42,y,z+J()*.3,0,0,.8);});
    for(let y=PL+.2;y<BB;y+=.25){L([.85,y,-1.95],[1.3,y,-1.95],{b:.3,d:22});L([5.95,y,-1.95],[6.5,y,-1.95],{b:.3,d:22});L([10.4,y,.05],[11.2,y,.05],{b:.3,d:22});}
    [1.45,2.65,3.85,5.05].forEach(x=>wZ(x,x+.75,PL,2.6,-1.95,6));
    [-1.5,-.3,.9,2.1].forEach(z=>wX(z,z+.75,PL,2.6,0,6));
    wZ(8.0,8.8,PL,2.6,-.02,0);wZ(7.45,7.95,PL,2.6,-.02,0);wZ(8.85,9.35,PL,2.6,-.02,0);
    [[-.3,.32],[-.6,.18],[-.9,.04]].forEach(s=>L([7.2,s[1],s[0]],[9.6,s[1],s[0]],{b:.55,d:32}));
    const KY0=1.4,KY1=2.62;
    poly([[11.2,KY0,0],[14.3,KY0,0],[14.3,KY0,1.8]],{r:2,d:60},true);poly([[11.2,KY1,0],[14.3,KY1,0],[14.3,KY1,1.8]],{r:2,d:60},true);
    L([11.2,KY0,0],[11.2,KY1,0],{r:2});L([14.3,KY0,1.8],[14.3,KY1,1.8],{r:2});L([14.3,KY0,0],[14.3,KY1,0],{r:2,b:.6});
    fill([11.2,KY0,0],[3.1,0,0],[0,KY1-KY0,0],2);fill([14.3,KY0,0],[0,0,1.8],[0,KY1-KY0,0],2);
    poly([[11.2,PL,0],[11.2,KY0,0],[14.3,KY0,0],[14.3,KY0,1.8],[14.3,PL,1.8]],{b:.6,d:32},true);L([14.3,PL,0],[14.3,KY0,0],{b:.8});
    wX(5.4,8.3,PL,2.65,14.3,1,false);wX(8.9,11.45,PL,2.65,14.3,1,true);
    wZ(12.2,14.3,PL,2.65,11.45,1,true);wZ(8.2,10.4,PL,2.65,11.45,1,false);wZ(4.6,6.8,PL,2.65,11.45,7,true);
    poly([[6.45,BT+1,-1.95],[10.35,BT+1,-1.95],[10.35,BT+1,-.2]],{b:.65,d:38},true);[6.45,8.4,10.35].forEach(x=>L([x,BT,-1.95],[x,BT+1,-1.95],{b:.6}));L([10.35,BT,-.2],[10.35,BT+1,-.2],{b:.6});
    function drawD(o){const M=o.m,hw=o.hw,eo=o.eo,yt=o.yt,yp=o.yp+.15,yE=o.yE,ov=-o.ov;
      poly([M(-hw,o.yb,0),M(hw,o.yb,0),M(hw,yt,0),M(0,o.yp,0),M(-hw,yt,0)],{d:60});
      poly([M(-hw-eo,yE,ov),M(0,yp,ov),M(hw+eo,yE,ov)],{d:70},true);
      poly([M(-hw-eo+.15,yE-.28,ov),M(0,yp-.32,ov),M(hw+eo-.15,yE-.28,ov)],{d:50,b:.8},true);
      L(M(-hw-eo,yE,ov),M(-hw-eo+.15,yE-.28,ov),{b:.8});L(M(hw+eo,yE,ov),M(hw+eo-.15,yE-.28,ov),{b:.8});
      L(M(0,yp,ov),M(0,yp,o.dR),{d:60});L(M(-hw-eo,yE,ov),M(-hw-eo,yE,o.dEl));L(M(hw+eo,yE,ov),M(hw+eo,yE,o.dEr));
      L(M(-hw-eo,yE,o.dEl),M(0,yp,o.dR));L(M(hw+eo,yE,o.dEr),M(0,yp,o.dR));
      L(M(-hw,o.yb,0),M(-hw,yt,o.reach(-hw,yt)),{b:.6});L(M(hw,o.yb,0),M(hw,yt,o.reach(hw,yt)),{b:.6});
      for(let s=.33;s<.95;s+=.33){const y=yE+(yp-yE)*s,u=(hw+eo)*(1-s);L(M(-u,y,ov),M(-u,y,o.dEl+(o.dR-o.dEl)*s),{b:.28,d:16});L(M(u,y,ov),M(u,y,o.dEr+(o.dR-o.dEr)*s),{b:.28,d:16});}
      L(M(-hw+.3,yt,.02),M(0,o.yp-.4,.02),{b:.45});L(M(hw-.3,yt,.02),M(0,o.yp-.4,.02),{b:.45});
      if(o.base)L(M(-hw,o.yb+o.base,0),M(hw,o.yb+o.base,0),{b:.7});
      const a=M(-o.ww/2,o.wy[0],0),b=M(o.ww/2,o.wy[0],0);win(a,[b[0]-a[0],0,b[2]-a[2]],[0,o.wy[1]-o.wy[0],0],o.r,true);}
    D.forEach(drawD);
    [[5.2,6.0,5],[6.3,7.1,5],[8.5,9.3,8],[9.6,10.4,8]].forEach(x=>{const d0=1.9,d1=3.0;win([x[0],BT+d0*K,11.45-d0],[x[1]-x[0],0,0],[0,(d1-d0)*K,-(d1-d0)],x[2],false);});
    [[6.8,7.6,9],[3.5,4.3,10]].forEach(z=>{const d0=1.9,d1=3.0;win([d0,BT+d0*K,z[0]],[0,0,z[1]-z[0]],[d1-d0,(d1-d0)*K,0],z[2],false);});
    for(let i=0;i<1300;i++)pt(-6+Math.random()*28,Math.random()*12,-9+Math.random()*28,2,0,1);
    for(let i=0;i<P.length;i+=3)P[i]=14.3-P[i];
    
const n = P.length / 3;
const order = Array.from({ length: n }, (_, i) => i);
for (let i = n - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [order[i], order[j]] = [order[j], order[i]]; }
const buf = Buffer.alloc(8 + n * 7 * 4);
buf.write('DOMH', 0, 'ascii');
buf.writeInt32LE(n, 4);
let o = 8;
for (const i of order) {
  for (const v of [P[i * 3], P[i * 3 + 1], P[i * 3 + 2], A[i * 4], A[i * 4 + 1], A[i * 4 + 2], A[i * 4 + 3]]) { buf.writeFloatLE(v, o); o += 4; }
}
const outFile = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'house.bin');
fs.writeFileSync(outFile, buf);
console.log('Zapisano', n, 'punktów do', outFile);
